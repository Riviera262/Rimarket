package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.entity.*;
import RiMarket.com.example.demo.module.catalog.exception.CatalogErrorCode;
import RiMarket.com.example.demo.module.catalog.mapper.VariantImageMapper;
import RiMarket.com.example.demo.module.catalog.port.StoredImage;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VariantImageCommandServiceTest {
    @Mock ProductRepository repository;
    @Mock EntityManager entityManager;
    VariantImageCommandService service;
    Product product;
    UUID productId = UUID.randomUUID();
    UUID variantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VariantImageCommandService(repository, new VariantImageMapper());
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        Category root = new Category("Devices", null);
        product = new Product("Laptop", "laptop", null, new Category("Laptops", root));
        ReflectionTestUtils.setField(product, "id", productId);
        ProductVariant variant = product.addVariant("SKU", "Black", BigDecimal.TEN);
        ReflectionTestUtils.setField(variant, "id", variantId);
    }

    @Test
    void attachUsesRootLockAndMapsGeneratedIdAfterFlush() {
        commandLoad();
        UUID imageId = UUID.randomUUID();
        doAnswer(call -> {
            ReflectionTestUtils.setField(product.getVariantImages(variantId).getFirst(), "id", imageId);
            return null;
        }).when(repository).flush();
        var response = service.attachImage(productId, variantId, new StoredImage("asset", "https://cdn.test/a"));
        assertEquals(imageId, response.id());
        var order = inOrder(repository, entityManager);
        order.verify(repository).findAggregateForChildCommandById(productId);
        order.verify(entityManager).lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        order.verify(repository).findAllWithImagesByProductId(productId);
        order.verify(repository).flush();
    }

    @Test
    void attachesWholeBatchWithOneRootLockAndOneFlush() {
        commandLoad();
        UUID firstImageId = UUID.randomUUID();
        UUID secondImageId = UUID.randomUUID();
        doAnswer(call -> {
            List<VariantImage> images = product.getVariantImages(variantId);
            ReflectionTestUtils.setField(images.get(0), "id", firstImageId);
            ReflectionTestUtils.setField(images.get(1), "id", secondImageId);
            return null;
        }).when(repository).flush();

        var responses = service.attachImages(productId, variantId, List.of(
                new StoredImage("first", "https://cdn.test/first"),
                new StoredImage("second", "https://cdn.test/second")
        ));

        assertEquals(List.of(firstImageId, secondImageId), responses.stream().map(r -> r.id()).toList());
        assertEquals(List.of(0, 1), responses.stream().map(r -> r.sortOrder()).toList());
        var order = inOrder(repository, entityManager);
        order.verify(repository).findAggregateForChildCommandById(productId);
        order.verify(entityManager).lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        order.verify(repository).findAllWithImagesByProductId(productId);
        order.verify(repository).flush();
    }

    @Test
    void rejectsOversizedBatchBeforePartiallyAttachingMetadata() {
        for (int i = 0; i < 7; i++) {
            addImage("asset-" + i);
        }
        commandLoad();

        var error = assertThrows(CatalogDomainException.class, () -> service.attachImages(
                productId,
                variantId,
                List.of(
                        new StoredImage("eighth", "https://cdn.test/8"),
                        new StoredImage("ninth", "https://cdn.test/9")
                )
        ));

        assertEquals(CatalogDomainViolation.VARIANT_IMAGE_LIMIT_EXCEEDED, error.getViolation());
        assertEquals(7, product.getVariantImages(variantId).size());
        verify(repository, never()).flush();
    }

    @Test
    void rechecksImageLimitAtAttachEvenAfterSuccessfulPrecheck() {
        when(repository.findAggregateById(productId)).thenReturn(Optional.of(product));
        service.ensureCanAddImage(productId, variantId);
        for (int i = 0; i < 8; i++) addImage("asset-" + i);
        commandLoad();
        var error = assertThrows(CatalogDomainException.class,
                () -> service.attachImage(productId, variantId, new StoredImage("ninth", "https://cdn.test/9")));
        assertEquals(CatalogDomainViolation.VARIANT_IMAGE_LIMIT_EXCEEDED, error.getViolation());
        assertEquals(8, product.getVariantImages(variantId).size());
        verify(repository, never()).flush();
    }

    @Test
    void replaceReturnsActualPreviousAssetAndPreservesIdentity() {
        VariantImage image = addImage("old");
        commandLoad();
        var result = service.replaceImage(productId, variantId, image.getId(),
                new StoredImage("new", "https://cdn.test/new"));
        assertEquals("old", result.previousPublicId());
        assertEquals(image.getId(), result.image().id());
        assertEquals("https://cdn.test/new", result.image().url());
        assertEquals(0, result.image().sortOrder());
        verify(repository).findAggregateForChildCommandById(productId);
    }

    @Test
    void removeUsesRootLockAndReturnsAssetForCleanup() {
        VariantImage image = addImage("old");
        commandLoad();
        assertEquals("old", service.removeImage(productId, variantId, image.getId()));
        assertTrue(product.getVariantImages(variantId).isEmpty());
        verify(repository).findAggregateForChildCommandById(productId);
    }

    @Test
    void reorderUsesRootLockAndUpdatesCover() {
        VariantImage first = addImage("first");
        VariantImage second = addImage("second");
        commandLoad();
        service.reorderImages(productId, variantId, List.of(second.getId(), first.getId()));
        assertSame(second, product.getVariantImages(variantId).getFirst());
        verify(repository).findAggregateForChildCommandById(productId);
    }

    @Test
    void readsEmptyImagesWithoutWriteLock() {
        when(repository.findAggregateById(productId)).thenReturn(Optional.of(product));
        assertTrue(service.getImages(productId, variantId).isEmpty());
        verify(repository, never()).findAggregateForChildCommandById(productId);
    }

    @Test
    void rejectsImageFromAnotherVariant() {
        var other = product.addVariant("OTHER", "White", BigDecimal.ONE);
        UUID otherId = UUID.randomUUID();
        ReflectionTestUtils.setField(other, "id", otherId);
        var image = product.addVariantImage(otherId, "other", "https://cdn.test/other");
        ReflectionTestUtils.setField(image, "id", UUID.randomUUID());
        commandLoad();
        var error = assertThrows(CatalogDomainException.class,
                () -> service.removeImage(productId, variantId, image.getId()));
        assertEquals(CatalogDomainViolation.VARIANT_IMAGE_NOT_IN_VARIANT, error.getViolation());
        assertEquals(1, other.getImages().size());
    }

    @Test
    void missingProductDoesNotLoadImages() {
        var error = assertThrows(AppException.class, () -> service.getImages(productId, variantId));
        assertEquals(CatalogErrorCode.PRODUCT_NOT_FOUND, error.getErrorCode());
        verify(repository, never()).findAllWithImagesByProductId(any());
    }

    private void commandLoad() {
        when(repository.findAggregateForChildCommandById(productId)).thenReturn(Optional.of(product));
    }

    private VariantImage addImage(String asset) {
        var image = product.addVariantImage(variantId, asset, "https://cdn.test/" + asset);
        ReflectionTestUtils.setField(image, "id", UUID.randomUUID());
        return image;
    }
}
