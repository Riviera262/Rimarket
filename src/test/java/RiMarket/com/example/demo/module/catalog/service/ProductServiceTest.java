package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.domain.identifier.SlugGenerator;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.dto.product.CreateProductRequest;
import RiMarket.com.example.demo.module.catalog.dto.product.ProductSummaryResponse;
import RiMarket.com.example.demo.module.catalog.dto.product.UpdateProductRequest;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.exception.CatalogErrorCode;
import RiMarket.com.example.demo.module.catalog.mapper.ProductMapper;
import RiMarket.com.example.demo.module.catalog.mapper.ProductVariantMapper;
import RiMarket.com.example.demo.module.catalog.mapper.VariantImageMapper;
import RiMarket.com.example.demo.module.catalog.repository.CategoryRepository;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import RiMarket.com.example.demo.shared.exception.AppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private SlugGenerator slugGenerator;

    @Mock
    private EntityManager entityManager;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        ProductMapper productMapper = new ProductMapper(
                new ProductVariantMapper(new VariantImageMapper())
        );
        productService = new ProductService(
                productRepository,
                categoryRepository,
                productMapper,
                slugGenerator
        );
        ReflectionTestUtils.setField(productService, "entityManager", entityManager);
    }

    @Test
    void createsInactiveDraftWithServerGeneratedSlug() {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Category category = category("Electronics", categoryId);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(slugGenerator.generate("Gaming Laptop")).thenReturn("gaming-laptop");
        when(productRepository.existsBySlugIgnoreCase("gaming-laptop")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            ReflectionTestUtils.setField(product, "id", productId);
            return product;
        });

        ProductSummaryResponse response = productService.createProduct(
                new CreateProductRequest("Gaming Laptop", "Draft", categoryId)
        );

        assertEquals(productId, response.productId());
        assertEquals("gaming-laptop", response.slug());
        assertFalse(response.active());
    }

    @Test
    void appendsEntropyWhenGeneratedSlugAlreadyExists() {
        UUID categoryId = UUID.randomUUID();
        Category category = category("Electronics", categoryId);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(slugGenerator.generate("Gaming Laptop")).thenReturn("gaming-laptop");
        when(productRepository.existsBySlugIgnoreCase("gaming-laptop")).thenReturn(true);
        when(slugGenerator.appendEntropy("gaming-laptop")).thenReturn("gaming-laptop-a1b2c3d4");
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(
                new CreateProductRequest("Gaming Laptop", null, categoryId)
        );

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertEquals("gaming-laptop-a1b2c3d4", captor.getValue().getSlug());
    }

    @Test
    void rejectsCreatingProductInRootCategory() {
        UUID rootId = UUID.randomUUID();
        Category root = rootCategory("Electronics", rootId);
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(slugGenerator.generate("Gaming Laptop")).thenReturn("gaming-laptop");
        when(productRepository.existsBySlugIgnoreCase("gaming-laptop")).thenReturn(false);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> productService.createProduct(
                        new CreateProductRequest("Gaming Laptop", null, rootId)
                )
        );

        assertEquals(CatalogDomainViolation.PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT, exception.getViolation());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updatesProductAndMovesItToAnotherCategoryWhileKeepingStableSlug() {
        UUID productId = UUID.randomUUID();
        UUID oldCategoryId = UUID.randomUUID();
        UUID newCategoryId = UUID.randomUUID();
        Category oldCategory = category("Old category", oldCategoryId);
        Category newCategory = category("New category", newCategoryId);
        Product product = product("Old name", "stable-slug", oldCategory, productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(newCategoryId)).thenReturn(Optional.of(newCategory));

        ProductSummaryResponse response = productService.updateProduct(
                productId,
                new UpdateProductRequest("New name", "New description", newCategoryId)
        );

        assertEquals("New name", product.getName());
        assertEquals("New description", product.getDescription());
        assertSame(newCategory, product.getCategory());
        assertEquals("stable-slug", response.slug());
        verify(slugGenerator, never()).generate("New name");
    }

    @Test
    void rejectsMovingProductToRootBeforeChangingOtherFields() {
        UUID productId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        Category currentCategory = category("Laptops", UUID.randomUUID());
        Category root = rootCategory("Electronics", rootId);
        Product product = product("Old name", "stable-slug", currentCategory, productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(rootId)).thenReturn(Optional.of(root));

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> productService.updateProduct(
                        productId,
                        new UpdateProductRequest("New name", "New description", rootId)
                )
        );

        assertEquals(CatalogDomainViolation.PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT, exception.getViolation());
        assertEquals("Old name", product.getName());
        assertSame(currentCategory, product.getCategory());
    }

    @Test
    void activatesProductLoadedWithItsVariants() {
        UUID productId = UUID.randomUUID();
        Product product = product(
                "Gaming Laptop",
                "gaming-laptop",
                category("Electronics", UUID.randomUUID()),
                productId
        );
        product.addVariant("LAPTOP-16GB", "16 GB", BigDecimal.valueOf(1_000));
        product.deactivate();
        when(productRepository.findAggregateForChildCommandById(productId)).thenReturn(Optional.of(product));

        productService.activateProduct(productId);

        assertTrue(product.isActive());
        verify(productRepository).findAggregateForChildCommandById(productId);
        verify(entityManager).lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void refusesToDeleteAnActiveProduct() {
        UUID productId = UUID.randomUUID();
        Product product = product(
                "Gaming Laptop",
                "gaming-laptop",
                category("Electronics", UUID.randomUUID()),
                productId
        );
        product.addVariant("LAPTOP-16GB", "16 GB", BigDecimal.valueOf(1_000));
        product.activate();
        when(productRepository.findAggregateById(productId)).thenReturn(Optional.of(product));

        AppException exception = assertThrows(
                AppException.class,
                () -> productService.deleteDraftProduct(productId)
        );

        assertEquals(CatalogErrorCode.ACTIVE_PRODUCT_CANNOT_BE_DELETED, exception.getErrorCode());
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void deletesInactiveDraftWithoutVariants() {
        UUID productId = UUID.randomUUID();
        Product product = product(
                "Gaming Laptop",
                "gaming-laptop",
                category("Electronics", UUID.randomUUID()),
                productId
        );
        when(productRepository.findAggregateById(productId)).thenReturn(Optional.of(product));

        productService.deleteDraftProduct(productId);

        verify(productRepository).delete(product);
    }

    private Category category(String name, UUID id) {
        Category root = rootCategory(name + " root", UUID.randomUUID());
        Category category = new Category(name, root);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private Category rootCategory(String name, UUID id) {
        Category category = new Category(name, null);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private Product product(String name, String slug, Category category, UUID id) {
        Product product = new Product(name, slug, null, category);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}
