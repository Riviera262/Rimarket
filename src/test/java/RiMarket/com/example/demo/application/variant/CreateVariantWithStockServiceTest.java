package RiMarket.com.example.demo.application.variant;

import RiMarket.com.example.demo.module.catalog.api.CatalogVariantCommands;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.CreateProductVariantRequest;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.inventory.api.InventoryItemCommands;
import RiMarket.com.example.demo.module.inventory.domain.InventoryActor;
import RiMarket.com.example.demo.module.inventory.dto.InventoryItemResponse;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Optional;
import RiMarket.com.example.demo.shared.exception.AppException;
import RiMarket.com.example.demo.shared.exception.CommonErrorCode;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateVariantWithStockServiceTest {
    @Mock CatalogVariantCommands catalog;
    @Mock InventoryItemCommands inventory;
    @Mock VariantCreationRequestStore requestStore;
    CreateVariantWithStockService service;
    final UUID productId = UUID.randomUUID();
    final UUID variantId = UUID.randomUUID();
    final UUID operationId = UUID.randomUUID();
    final InventoryActor actor = InventoryActor.user(UUID.randomUUID(), "Test staff");

    @BeforeEach
    void setUp() {
        service = new CreateVariantWithStockService(catalog, inventory, requestStore);
    }

    @Test
    void createsCatalogFirstThenPassesItsIdQuantityAndTrustedActorToInventory() {
        ProductVariantResponse variant = variant();
        InventoryItemResponse item = new InventoryItemResponse(variantId, "SKU", "Product", "Black",
                10, 0, 10, 1L, null, null);
        when(catalog.addVariant(productId, variantRequest())).thenReturn(variant);
        when(inventory.createItemWithStock(variantId, 10, operationId, actor)).thenReturn(item);

        CreateVariantWithStockResponse response = service.create(productId, request(10), actor);

        assertSame(variant, response.variant());
        assertSame(item, response.inventory());
        var order = inOrder(requestStore, catalog, inventory);
        order.verify(requestStore).claimOrReplay(actor.id(), operationId, VariantCreationFingerprint.of(productId, request(10)));
        order.verify(catalog).addVariant(productId, variantRequest());
        order.verify(inventory).createItemWithStock(variantId, 10, operationId, actor);
        order.verify(requestStore).complete(actor.id(), operationId, response);
    }

    @Test
    void stillInitializesAnItemWhenQuantityIsZero() {
        when(catalog.addVariant(productId, variantRequest())).thenReturn(variant());
        service.create(productId, request(0), actor);
        verify(inventory).createItemWithStock(variantId, 0, operationId, actor);
    }

    @Test
    void doesNotCallInventoryWhenCatalogFails() {
        RuntimeException failure = new IllegalStateException("Catalog failure");
        when(catalog.addVariant(productId, variantRequest())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.create(productId, request(10), actor)));
        verifyNoInteractions(inventory);
        verify(requestStore, never()).complete(any(), any(), any());
    }

    @Test
    void doesNotHideInventoryFailureFromTransactionBoundary() {
        when(catalog.addVariant(productId, variantRequest())).thenReturn(variant());
        RuntimeException failure = new IllegalStateException("Inventory failure");
        when(inventory.createItemWithStock(variantId, 10, operationId, actor)).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.create(productId, request(10), actor)));
        verify(requestStore, never()).complete(any(), any(), any());
    }

    @Test
    void replayReturnsOriginalResponseWithoutCallingEitherModule() {
        CreateVariantWithStockResponse original = new CreateVariantWithStockResponse(variant(),
                new InventoryItemResponse(variantId, "SKU", "Product", "Black", 0, 0, 0, 0L, null, null));
        when(requestStore.claimOrReplay(actor.id(), operationId, VariantCreationFingerprint.of(productId, request(0))))
                .thenReturn(Optional.of(original));
        assertSame(original, service.create(productId, request(0), actor));
        verifyNoInteractions(catalog, inventory);
        verify(requestStore, never()).complete(any(), any(), any());
    }

    @Test
    void mismatchedPayloadStopsBeforeBusinessWrites() {
        when(requestStore.claimOrReplay(any(), any(), any()))
                .thenThrow(new AppException(CommonErrorCode.IDEMPOTENCY_CONFLICT));
        assertEquals(CommonErrorCode.IDEMPOTENCY_CONFLICT,
                assertThrows(AppException.class, () -> service.create(productId, request(10), actor)).getErrorCode());
        verifyNoInteractions(catalog, inventory);
    }

    @Test
    void responsePersistenceFailurePropagatesSoTheOuterTransactionCanRollBack() {
        when(catalog.addVariant(productId, variantRequest())).thenReturn(variant());
        doThrow(new IllegalStateException("Storage failure")).when(requestStore).complete(any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.create(productId, request(0), actor));
    }

    @Test
    void validatesRequiredQuantityAndOperationId() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(0)).isEmpty());
            assertFalse(validator.validate(request(-1)).isEmpty());
            assertFalse(validator.validate(request(null)).isEmpty());
            assertFalse(validator.validate(new CreateVariantWithStockRequest(
                    "Black", BigDecimal.TEN, 10, null)).isEmpty());
        }
    }

    private CreateVariantWithStockRequest request(Integer quantity) {
        return new CreateVariantWithStockRequest("Black", BigDecimal.TEN, quantity, operationId);
    }

    private CreateProductVariantRequest variantRequest() {
        return new CreateProductVariantRequest("Black", BigDecimal.TEN);
    }

    private ProductVariantResponse variant() {
        return new ProductVariantResponse(variantId, "SKU", "Black", BigDecimal.TEN, 0, true);
    }
}
