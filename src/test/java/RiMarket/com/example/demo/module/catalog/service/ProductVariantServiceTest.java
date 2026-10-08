package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.identifier.SkuGenerator;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.CreateProductVariantRequest;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.UpdateProductVariantRequest;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.entity.ProductVariant;
import RiMarket.com.example.demo.module.catalog.exception.CatalogErrorCode;
import RiMarket.com.example.demo.module.catalog.mapper.ProductVariantMapper;
import RiMarket.com.example.demo.module.catalog.mapper.VariantImageMapper;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import RiMarket.com.example.demo.shared.exception.AppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SkuGenerator skuGenerator;

    @Mock
    private EntityManager entityManager;

    private ProductVariantService productVariantService;

    @BeforeEach
    void setUp() {
        productVariantService = new ProductVariantService(
                new ProductVariantMapper(new VariantImageMapper()),
                productRepository,
                skuGenerator
        );
        ReflectionTestUtils.setField(productVariantService, "entityManager", entityManager);
    }

    @Test
    void addsVariantThroughProductAggregateAndFlushesForGeneratedId() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId);

        when(productRepository.findAggregateForChildCommandById(productId))
                .thenReturn(Optional.of(product));
        when(skuGenerator.generate("Gaming Laptop", "16 GB"))
                .thenReturn("GAMING-LAPTOP-16GB-A1B2C3D4");
        when(productRepository.existsByVariantsSkuIgnoreCase("GAMING-LAPTOP-16GB-A1B2C3D4"))
                .thenReturn(false);

        ProductVariantResponse response = productVariantService.addVariant(
                productId,
                new CreateProductVariantRequest("16 GB", BigDecimal.valueOf(1_000))
        );

        assertEquals(1, product.getVariants().size());
        assertEquals("GAMING-LAPTOP-16GB-A1B2C3D4", response.sku());
        assertEquals("16 GB", response.name());
        assertTrue(response.active());
        assertTrue(product.isActive());
        verify(productRepository).flush();
        verify(productRepository).findAggregateForChildCommandById(productId);
        verify(entityManager).lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void rejectsGeneratedSkuCollisionBeforeChangingAggregate() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId);

        when(productRepository.findAggregateForChildCommandById(productId))
                .thenReturn(Optional.of(product));
        when(skuGenerator.generate("Gaming Laptop", "16 GB"))
                .thenReturn("GAMING-LAPTOP-16GB-A1B2C3D4");
        when(productRepository.existsByVariantsSkuIgnoreCase("GAMING-LAPTOP-16GB-A1B2C3D4"))
                .thenReturn(true);

        AppException exception = assertThrows(
                AppException.class,
                () -> productVariantService.addVariant(
                        productId,
                        new CreateProductVariantRequest("16 GB", BigDecimal.valueOf(1_000))
                )
        );

        assertEquals(CatalogErrorCode.SKU_ALREADY_EXISTS, exception.getErrorCode());
        assertTrue(product.getVariants().isEmpty());
        verify(productRepository, never()).flush();
    }

    @Test
    void updatesVariantThroughProductAggregate() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = product(productId);
        ProductVariant variant = product.addVariant(
                "GAMING-LAPTOP-16GB-A1B2C3D4",
                "16 GB",
                BigDecimal.valueOf(1_000)
        );
        ReflectionTestUtils.setField(variant, "id", variantId);
        when(productRepository.findAggregateForChildCommandById(productId))
                .thenReturn(Optional.of(product));

        ProductVariantResponse response = productVariantService.updateVariant(
                productId,
                variantId,
                new UpdateProductVariantRequest("32 GB", BigDecimal.valueOf(2_000))
        );

        assertEquals("32 GB", response.name());
        assertEquals(BigDecimal.valueOf(2_000).setScale(0), response.price());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void protectsLastActiveVariantWhenProductIsActive() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = product(productId);
        ProductVariant variant = product.addVariant(
                "GAMING-LAPTOP-16GB-A1B2C3D4",
                "16 GB",
                BigDecimal.valueOf(1_000)
        );
        ReflectionTestUtils.setField(variant, "id", variantId);
        product.activate();
        when(productRepository.findAggregateForChildCommandById(productId))
                .thenReturn(Optional.of(product));

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> productVariantService.deactivateVariant(productId, variantId)
        );

        assertEquals(CatalogDomainViolation.LAST_ACTIVE_VARIANT_CANNOT_BE_DEACTIVATED, exception.getViolation());
        assertTrue(variant.isActive());
    }

    @Test
    void reportsProductNotFoundBeforeVariantMutation() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findAggregateForChildCommandById(productId))
                .thenReturn(Optional.empty());

        AppException exception = assertThrows(
                AppException.class,
                () -> productVariantService.activateVariant(productId, UUID.randomUUID())
        );

        assertEquals(CatalogErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    private Product product(UUID id) {
        Category root = new Category("Electronics", null);
        Category category = new Category("Laptops", root);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());

        Product product = new Product("Gaming Laptop", "gaming-laptop", null, category);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
