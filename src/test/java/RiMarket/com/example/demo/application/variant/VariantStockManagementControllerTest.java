package RiMarket.com.example.demo.application.variant;

import RiMarket.com.example.demo.module.catalog.controller.management.ProductVariantManagementController;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.catalog.service.ProductVariantService;
import RiMarket.com.example.demo.module.inventory.domain.InventoryActor;
import RiMarket.com.example.demo.module.inventory.controller.InventoryManagementController;
import RiMarket.com.example.demo.module.inventory.service.InventoryService;
import RiMarket.com.example.demo.module.inventory.dto.InventoryItemResponse;
import RiMarket.com.example.demo.shared.security.SecurityUser;
import RiMarket.com.example.demo.shared.security.JwtService;
import RiMarket.com.example.demo.shared.security.JwtAuthFilter;
import RiMarket.com.example.demo.shared.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.Date;
import javax.crypto.SecretKey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT filter and principal resolution; full SecurityFilterChain/role enforcement is not loaded. */
class VariantStockManagementControllerTest {
    private final CreateVariantWithStockService workflow = mock(CreateVariantWithStockService.class);
    private final ProductVariantService catalog = mock(ProductVariantService.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final UUID productId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID operationId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private MockMvc mvc;
    private SecretKey signingKey;
    private String accessToken;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        // Ephemeral key generated only in memory; never loads application secrets or logs tokens.
        signingKey = Jwts.SIG.HS256.key().build();
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", Encoders.BASE64.encode(signingKey.getEncoded()));
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 120L);
        SecurityUser principal = mock(SecurityUser.class);
        when(principal.getUserId()).thenReturn(actorId);
        when(principal.getUsername()).thenReturn("Test staff");
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_STAFF"))).when(principal).getAuthorities();
        accessToken = jwtService.generateAccessToken(principal);
        mvc = MockMvcBuilders.standaloneSetup(
                        new VariantStockManagementController(workflow),
                        new ProductVariantManagementController(catalog),
                        new InventoryManagementController(inventoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new JwtAuthFilter(jwtService))
                .defaultRequest(post("/").header("Authorization", "Bearer " + accessToken))
                .build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void canonicalCreateRouteCallsOnlyOrchestratorAndReturnsVariantAndInventory() throws Exception {
        ProductVariantResponse variant = new ProductVariantResponse(
                variantId, "SKU", "Black", BigDecimal.TEN, 0, true);
        InventoryItemResponse inventory = new InventoryItemResponse(
                variantId, "SKU", "Product", "Black", 0, 0, 0, 0L, null, null);
        when(workflow.create(eq(productId), any(), any())).thenReturn(
                new CreateVariantWithStockResponse(variant, inventory));

        mvc.perform(post(basePath()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variant.id").value(variantId.toString()))
                .andExpect(jsonPath("$.inventory.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.inventory.onHandQty").value(0));
        verify(workflow).create(eq(productId), any(), eq(InventoryActor.user(actorId, "Test staff")));
        verifyNoInteractions(catalog);
    }

    @Test
    void oldWithStockAliasIsNotAnotherCreationDoor() throws Exception {
        // The existing PUT /{variantId} pattern may match the path, but POST must not dispatch.
        mvc.perform(post(basePath() + "/with-stock").contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(workflow, catalog);
    }

    @Test
    void oldCatalogOnlyPayloadCannotBypassInventoryInitialization() throws Exception {
        mvc.perform(post(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Black\",\"price\":10}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workflow, catalog);
    }

    @Test
    void inventoryDoesNotExposeAnotherHttpCreateEndpoint() throws Exception {
        mvc.perform(post("/api/management/inventory/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + variantId + "\"}"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(inventoryService, workflow, catalog);
    }

    @Test
    void updateStillBelongsToCatalog() throws Exception {
        mvc.perform(put(basePath() + "/" + variantId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"White\",\"price\":20}"))
                .andExpect(status().isOk());
        verify(catalog).updateVariant(eq(productId), eq(variantId), any());
        verifyNoInteractions(workflow);
    }

    @Test
    void cookieTokenSuppliesActorToReceiptAndPreservesNameAndRoles() throws Exception {
        mvc.perform(post("/api/management/inventory/items/{variantId}/receipts", variantId)
                        .header("Authorization", "")
                        .cookie(new Cookie("accessToken", accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2,\"operationId\":\"" + operationId + "\"}"))
                .andExpect(status().isOk());
        verify(inventoryService).receive(eq(variantId), any(), eq(InventoryActor.user(actorId, "Test staff")));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        org.junit.jupiter.api.Assertions.assertEquals("Test staff", authentication.getName());
        org.junit.jupiter.api.Assertions.assertTrue(authentication.getAuthorities().contains(
                new SimpleGrantedAuthority("ROLE_STAFF")));
    }

    @Test
    void bearerTokenSuppliesActorToAdjustment() throws Exception {
        mvc.perform(post("/api/management/inventory/items/{variantId}/adjustments", variantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delta\":-1,\"reason\":\"Damaged\",\"operationId\":\"" + operationId + "\"}"))
                .andExpect(status().isOk());
        verify(inventoryService).adjust(eq(variantId), any(), eq(InventoryActor.user(actorId, "Test staff")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-id", "bad-id", "expired", "bad-signature"})
    void unusableIdentityNeverReachesCreationAndReturns401(String scenario) throws Exception {
        var builder = Jwts.builder().subject("Test staff")
                .claim("roles", List.of("ROLE_STAFF"))
                .expiration(new Date(System.currentTimeMillis() + (scenario.equals("expired") ? -60000 : 60000)));
        if (!scenario.equals("missing-id")) {
            builder.claim("userId", scenario.equals("bad-id") ? "not-a-uuid" : actorId.toString());
        }
        SecretKey key = scenario.equals("bad-signature") ? Jwts.SIG.HS256.key().build() : signingKey;
        String token = builder.signWith(key).compact();
        mvc.perform(post(basePath()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(workflow, inventoryService, catalog);
    }

    @Test
    void missingAuthenticationReturns401InsteadOfNullPointerException() throws Exception {
        mvc.perform(post(basePath()).header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(workflow, inventoryService, catalog);
    }

    private String basePath() {
        return "/api/management/products/" + productId + "/variants";
    }

    private String body() {
        return "{\"name\":\"Black\",\"price\":10,\"initialQuantity\":0,\"operationId\":\"" + operationId + "\"}";
    }
}
