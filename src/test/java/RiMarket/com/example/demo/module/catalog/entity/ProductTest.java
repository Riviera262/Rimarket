package RiMarket.com.example.demo.module.catalog.entity;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProductTest {

    @Test
    void requiresCategoryWithoutDependingOnApplicationOrHttpErrors() {
        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> new Product("Laptop", "laptop", null, null)
        );

        assertEquals(CatalogDomainViolation.PRODUCT_CATEGORY_REQUIRED, exception.getViolation());
    }

    @Test
    void rejectsRootCategoryOnCreation() {
        Category root = new Category("Electronics", null);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> new Product("Laptop", "laptop", null, root)
        );

        assertEquals(CatalogDomainViolation.PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT, exception.getViolation());
    }

    @Test
    void rejectsMovingToRootWithoutChangingCurrentCategory() {
        Product product = product();
        Category currentCategory = product.getCategory();
        Category root = new Category("Root", null);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> product.moveToCategory(root)
        );

        assertEquals(CatalogDomainViolation.PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT, exception.getViolation());
        assertSame(currentCategory, product.getCategory());
    }

    @Test
    void requiresAnActiveVariantBeforeActivation() {
        Product product = product();

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                product::activate
        );

        assertEquals(CatalogDomainViolation.PRODUCT_REQUIRES_ACTIVE_VARIANT, exception.getViolation());
        assertFalse(product.isActive());
    }

    @Test
    void protectsTheLastActiveVariantOfAnActiveProduct() {
        Product product = product();
        ProductVariant variant = product.addVariant("LAPTOP-16GB", "16 GB", BigDecimal.valueOf(1_000));
        UUID variantId = setId(variant);
        product.activate();

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> product.deactivateVariant(variantId)
        );

        assertEquals(CatalogDomainViolation.LAST_ACTIVE_VARIANT_CANNOT_BE_DEACTIVATED, exception.getViolation());
        assertTrue(variant.isActive());
    }

    @Test
    void allowsDeactivatingOneOfMultipleActiveVariants() {
        Product product = product();
        ProductVariant first = product.addVariant("LAPTOP-16GB", "16 GB", BigDecimal.valueOf(1_000));
        ProductVariant second = product.addVariant("LAPTOP-32GB", "32 GB", BigDecimal.valueOf(2_000));
        UUID firstId = setId(first);
        setId(second);
        product.activate();

        assertDoesNotThrow(() -> product.deactivateVariant(firstId));

        assertFalse(first.isActive());
        assertTrue(second.isActive());
        assertTrue(product.isActive());
    }

    @Test
    void rejectsNegativeVariantPrice() {
        Product product = product();

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> product.addVariant("LAPTOP-16GB", "16 GB", BigDecimal.valueOf(-1))
        );

        assertEquals(CatalogDomainViolation.INVALID_VARIANT_PRICE, exception.getViolation());
    }

    @Test
    void rejectsFractionalVariantPriceForVnd() {
        Product product = product();

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> product.addVariant("LAPTOP-16GB", "16 GB", new BigDecimal("1000.50"))
        );

        assertEquals(CatalogDomainViolation.INVALID_VARIANT_PRICE, exception.getViolation());
    }

    @Test
    void rejectsBlankVariantName() {
        Product product = product();

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> product.addVariant("LAPTOP-16GB", "   ", BigDecimal.valueOf(1_000))
        );

        assertEquals(CatalogDomainViolation.INVALID_VARIANT_NAME, exception.getViolation());
    }

    @Test
    void rejectsBlankSku() {
        Product product = product();

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> product.addVariant("   ", "16 GB", BigDecimal.valueOf(1_000))
        );

        assertEquals(CatalogDomainViolation.INVALID_SKU, exception.getViolation());
    }

    @Test
    void duplicateNameIsRejectedEvenWhenInactiveAndInputHasPadding() {
        Product product = product();
        ProductVariant variant = product.addVariant("FIRST", "Black", BigDecimal.ONE);
        UUID id = setId(variant);
        product.deactivate();
        product.deactivateVariant(id);
        assertEquals(CatalogDomainViolation.DUPLICATE_VARIANT_NAME,
                assertThrows(CatalogDomainException.class,
                        () -> product.addVariant("SECOND", " Black ", BigDecimal.TEN)).getViolation());
        assertEquals(1, product.getVariants().size());
    }

    @Test
    void renameCannotTakeAnotherVariantsNameButCanKeepItsOwn() {
        Product product = product();
        ProductVariant first = product.addVariant("FIRST", "Black", BigDecimal.ONE);
        ProductVariant second = product.addVariant("SECOND", "White", BigDecimal.TEN);
        UUID firstId = setId(first);
        UUID secondId = setId(second);
        assertDoesNotThrow(() -> product.updateVariant(firstId, " Black ", BigDecimal.TEN));
        assertEquals(CatalogDomainViolation.DUPLICATE_VARIANT_NAME,
                assertThrows(CatalogDomainException.class,
                        () -> product.updateVariant(secondId, "Black", BigDecimal.ONE)).getViolation());
        assertEquals("White", second.getName());
        assertEquals(BigDecimal.TEN, second.getPrice());
    }

    @Test
    void sameNameAcrossProductsIsAllowedAndCaseRemainsSignificant() {
        Product product = product();
        product.addVariant("FIRST", "Black", BigDecimal.ONE);
        assertDoesNotThrow(() -> product().addVariant("SECOND", "Black", BigDecimal.ONE));
        assertDoesNotThrow(() -> product.addVariant("THIRD", "black", BigDecimal.ONE));
    }

    private Product product() {
        Category root = new Category("Electronics", null);
        return new Product(
                "Laptop",
                "laptop",
                null,
                new Category("Laptops", root)
        );
    }

    @Test
    void firstVariantPublishesProductButAddingAnotherDoesNotRepublish() {
        Product product = product();
        assertFalse(product.isActive());
        product.addVariant("FIRST", "First", BigDecimal.ONE);
        assertTrue(product.isActive());
        product.deactivate();
        product.addVariant("SECOND", "Second", BigDecimal.TEN);
        assertFalse(product.isActive());
    }

    @Test
    void activatingExistingVariantDoesNotRepublishProduct() {
        Product product = product();
        ProductVariant variant = product.addVariant("FIRST", "First", BigDecimal.ONE);
        UUID id = setId(variant);
        product.deactivate();
        product.deactivateVariant(id);
        product.activateVariant(id);
        assertTrue(variant.isActive());
        assertFalse(product.isActive());
    }

    @Test
    void reorderRejectsForeignIdBeforeChangingAnyPositions() {
        Product product = product();
        ProductVariant first = product.addVariant("FIRST", "First", BigDecimal.ONE);
        ProductVariant second = product.addVariant("SECOND", "Second", BigDecimal.ONE);
        UUID firstId = setId(first);
        UUID secondId = setId(second);
        assertThrows(CatalogDomainException.class,
                () -> product.reorderVariants(java.util.List.of(secondId, UUID.randomUUID())));
        assertEquals(0, first.getSortOrder());
        product.reorderVariants(java.util.List.of(secondId, firstId));
        assertEquals(second, product.getVariants().getFirst());
        assertEquals(0, second.getSortOrder());
        assertEquals(1, first.getSortOrder());
    }

    private UUID setId(ProductVariant variant) {
        UUID id = UUID.randomUUID();

        try {
            var idField = ProductVariant.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(variant, id);
            return id;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
