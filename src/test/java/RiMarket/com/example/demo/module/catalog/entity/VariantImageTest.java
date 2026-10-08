package RiMarket.com.example.demo.module.catalog.entity;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantImageTest {

    private Product product;
    private ProductVariant variant;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        Category root = new Category("Electronics", null);
        Category category = new Category("Laptops", root);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());

        product = new Product("Laptop", "laptop", null, category);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());

        variant = product.addVariant("LAPTOP-BLACK", "Black", BigDecimal.valueOf(1_000));
        variantId = UUID.randomUUID();
        ReflectionTestUtils.setField(variant, "id", variantId);
    }

    @Test
    void firstImageIsCoverAndNewImagesAreAppended() {
        VariantImage first = addImage("catalog/first", "https://cdn.test/first.jpg");
        VariantImage second = addImage("catalog/second", "https://cdn.test/second.jpg");

        assertEquals(0, first.getSortOrder());
        assertSame(first, product.getVariantImages(variantId).getFirst());
        assertEquals(1, second.getSortOrder());
    }

    @Test
    void movingImageToFirstPositionSelectsCover() {
        VariantImage first = addImage("catalog/first", "https://cdn.test/first.jpg");
        VariantImage second = addImage("catalog/second", "https://cdn.test/second.jpg");
        UUID secondId = setId(second);
        UUID firstId = setId(first);

        product.reorderVariantImages(variantId, List.of(secondId, firstId));

        assertSame(second, product.getVariantImages(variantId).getFirst());
        assertEquals(0, second.getSortOrder());
    }

    @Test
    void reordersCompleteImageListAndChangesCover() {
        VariantImage first = addImage("catalog/first", "https://cdn.test/first.jpg");
        VariantImage second = addImage("catalog/second", "https://cdn.test/second.jpg");
        VariantImage third = addImage("catalog/third", "https://cdn.test/third.jpg");
        UUID firstId = setId(first);
        UUID secondId = setId(second);
        UUID thirdId = setId(third);

        product.reorderVariantImages(variantId, List.of(thirdId, firstId, secondId));

        assertEquals(List.of(third, first, second), product.getVariantImages(variantId));
        assertEquals(0, third.getSortOrder());
        assertEquals(1, first.getSortOrder());
        assertEquals(2, second.getSortOrder());
        assertSame(third, product.getVariantImages(variantId).getFirst());
    }

    @Test
    void rejectsDuplicateIdsDuringReorder() {
        VariantImage first = addImage("catalog/first", "https://cdn.test/first.jpg");
        VariantImage second = addImage("catalog/second", "https://cdn.test/second.jpg");
        UUID firstId = setId(first);
        setId(second);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> product.reorderVariantImages(variantId, List.of(firstId, firstId))
        );

        assertEquals(CatalogDomainViolation.INVALID_VARIANT_IMAGE_ORDER, exception.getViolation());
    }

    @Test
    void removingCoverNormalizesOrderAndPromotesFirstRemainingImage() {
        VariantImage first = addImage("catalog/first", "https://cdn.test/first.jpg");
        VariantImage second = addImage("catalog/second", "https://cdn.test/second.jpg");
        UUID firstId = setId(first);
        setId(second);

        product.removeVariantImage(variantId, firstId);

        assertEquals(List.of(second), product.getVariantImages(variantId));
        assertEquals(0, second.getSortOrder());
        assertSame(second, product.getVariantImages(variantId).getFirst());
    }

    @Test
    void replacingStoredImagePreservesCatalogIdentityAndDisplayState() {
        VariantImage image = addImage("catalog/old", "https://cdn.test/old.jpg");
        UUID imageId = setId(image);

        VariantImage replaced = product.replaceVariantImage(
                variantId,
                imageId,
                "catalog/new",
                "https://cdn.test/new.jpg"
        );

        assertSame(image, replaced);
        assertEquals(imageId, replaced.getId());
        assertEquals("catalog/new", replaced.getPublicId());
        assertEquals("https://cdn.test/new.jpg", replaced.getUrl());
        assertEquals(0, replaced.getSortOrder());
        assertSame(replaced, product.getVariantImages(variantId).getFirst());
    }

    @Test
    void enforcesMaximumImagesPerVariant() {
        for (int index = 0; index < 8; index++) {
            addImage("catalog/image-" + index, "https://cdn.test/image-" + index + ".jpg");
        }

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> addImage("catalog/image-8", "https://cdn.test/image-8.jpg")
        );

        assertEquals(CatalogDomainViolation.VARIANT_IMAGE_LIMIT_EXCEEDED, exception.getViolation());
    }

    private VariantImage addImage(String publicId, String url) {
        return product.addVariantImage(variantId, publicId, url);
    }

    @Test
    void invalidReplacementDoesNotPartiallyChangeMetadata() {
        VariantImage image = addImage("old", "https://cdn.test/old");
        UUID id = setId(image);
        assertThrows(CatalogDomainException.class,
                () -> product.replaceVariantImage(variantId, id, "new", " "));
        assertEquals("old", image.getPublicId());
        assertEquals("https://cdn.test/old", image.getUrl());
    }

    @Test
    void rejectsMissingForeignAndNullIdsBeforeMutation() {
        var first = addImage("first", "https://cdn.test/1");
        var second = addImage("second", "https://cdn.test/2");
        UUID firstId = setId(first);
        UUID secondId = setId(second);
        assertThrows(CatalogDomainException.class, () -> product.reorderVariantImages(variantId, List.of(firstId)));
        assertThrows(CatalogDomainException.class,
                () -> product.reorderVariantImages(variantId, List.of(secondId, UUID.randomUUID())));
        assertThrows(CatalogDomainException.class,
                () -> product.reorderVariantImages(variantId, java.util.Arrays.asList(secondId, null)));
        assertThrows(CatalogDomainException.class, () -> product.reorderVariantImages(variantId, null));
        assertEquals(List.of(first, second), product.getVariantImages(variantId));
        assertEquals(0, first.getSortOrder());
    }

    @Test
    void replacingAtImageLimitIsAllowedAndRemovingLastImageLeavesEmptyList() {
        var image = addImage("old", "https://cdn.test/old");
        UUID id = setId(image);
        for (int i = 1; i < 8; i++) addImage("a" + i, "https://cdn.test/" + i);
        product.replaceVariantImage(variantId, id, "new", "https://cdn.test/new");
        assertEquals(8, product.getVariantImages(variantId).size());
        for (var current : List.copyOf(product.getVariantImages(variantId))) {
            UUID currentId = current.getId() == null ? setId(current) : current.getId();
            product.removeVariantImage(variantId, currentId);
        }
        assertTrue(product.getVariantImages(variantId).isEmpty());
    }

    private UUID setId(VariantImage image) {
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(image, "id", id);
        return id;
    }
}
