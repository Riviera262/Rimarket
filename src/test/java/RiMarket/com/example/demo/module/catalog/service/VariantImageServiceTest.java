package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.service.result.VariantImageReplacementResult;
import RiMarket.com.example.demo.module.catalog.dto.imageVariant.VariantImageResponse;
import RiMarket.com.example.demo.module.catalog.port.ImageStorage;
import RiMarket.com.example.demo.module.catalog.port.StoredImage;
import RiMarket.com.example.demo.shared.exception.AppException;
import RiMarket.com.example.demo.shared.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VariantImageServiceTest {

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private VariantImageCommandService commandService;

    private VariantImageService imageService;

    @BeforeEach
    void setUp() {
        imageService = new VariantImageService(imageStorage, commandService);
    }

    @Test
    void uploadsToStorageBeforeAttachingImageToCatalog() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        MockMultipartFile file = imageFile();
        StoredImage stored = storedImage("catalog/new", "https://cdn.test/new.jpg");
        VariantImageResponse expected = response(UUID.randomUUID(), stored.url());
        when(imageStorage.upload(any(byte[].class), eq(folder(variantId)))).thenReturn(stored);
        when(commandService.attachImage(productId, variantId, stored)).thenReturn(expected);

        VariantImageResponse actual = imageService.uploadImage(productId, variantId, file);

        assertSame(expected, actual);
        InOrder order = inOrder(commandService, imageStorage);
        order.verify(commandService).ensureCanAddImage(productId, variantId);
        order.verify(imageStorage).upload(any(byte[].class), eq(folder(variantId)));
        order.verify(commandService).attachImage(productId, variantId, stored);
    }

    @Test
    void compensatesNewUploadWhenCatalogAttachFails() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        MockMultipartFile file = imageFile();
        StoredImage stored = storedImage("catalog/new", "https://cdn.test/new.jpg");
        RuntimeException failure = new RuntimeException("database failed");
        when(imageStorage.upload(any(byte[].class), eq(folder(variantId)))).thenReturn(stored);
        when(commandService.attachImage(productId, variantId, stored)).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> imageService.uploadImage(productId, variantId, file)
        );

        assertSame(failure, thrown);
        verify(imageStorage).delete(stored.publicId());
    }

    @Test
    void uploadsBatchInRequestOrderAndAttachesItOnce() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        MockMultipartFile firstFile = imageFile("first.jpg", new byte[]{1});
        MockMultipartFile secondFile = imageFile("second.jpg", new byte[]{2});
        StoredImage firstStored = storedImage("catalog/first", "https://cdn.test/first.jpg");
        StoredImage secondStored = storedImage("catalog/second", "https://cdn.test/second.jpg");
        List<StoredImage> storedImages = List.of(firstStored, secondStored);
        List<VariantImageResponse> expected = List.of(
                response(UUID.randomUUID(), firstStored.url()),
                response(UUID.randomUUID(), secondStored.url())
        );
        when(imageStorage.upload(any(byte[].class), eq(folder(variantId))))
                .thenReturn(firstStored, secondStored);
        when(commandService.attachImages(productId, variantId, storedImages)).thenReturn(expected);

        List<VariantImageResponse> actual = imageService.uploadImages(
                productId,
                variantId,
                List.of(firstFile, secondFile)
        );

        assertSame(expected, actual);
        InOrder order = inOrder(commandService, imageStorage);
        order.verify(commandService).ensureCanAddImages(productId, variantId, 2);
        order.verify(imageStorage, times(2)).upload(any(byte[].class), eq(folder(variantId)));
        order.verify(commandService).attachImages(productId, variantId, storedImages);
    }

    @Test
    void validatesEveryBatchFileBeforeDatabaseOrStorageCalls() {
        MockMultipartFile unsupported = new MockMultipartFile(
                "files",
                "notes.txt",
                "text/plain",
                "not an image".getBytes()
        );

        AppException exception = assertThrows(
                AppException.class,
                () -> imageService.uploadImages(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        List.of(imageFile(), unsupported)
                )
        );

        assertEquals(CommonErrorCode.UNSUPPORTED_FILE_TYPE, exception.getErrorCode());
        verifyNoInteractions(commandService, imageStorage);
    }

    @Test
    void compensatesCompletedBatchUploadsWhenAFollowingUploadFails() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        StoredImage firstStored = storedImage("catalog/first", "https://cdn.test/first.jpg");
        when(imageStorage.upload(any(byte[].class), eq(folder(variantId))))
                .thenReturn(firstStored)
                .thenThrow(new RuntimeException("storage unavailable"));

        AppException exception = assertThrows(
                AppException.class,
                () -> imageService.uploadImages(
                        productId,
                        variantId,
                        List.of(imageFile("first.jpg", new byte[]{1}), imageFile("second.jpg", new byte[]{2}))
                )
        );

        assertEquals(CommonErrorCode.FILE_UPLOAD_FAILED, exception.getErrorCode());
        verify(imageStorage).delete(firstStored.publicId());
        verify(commandService, never()).attachImages(any(), any(), any());
    }

    @Test
    void compensatesEveryBatchUploadInReverseOrderWhenCatalogAttachFails() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        StoredImage firstStored = storedImage("catalog/first", "https://cdn.test/first.jpg");
        StoredImage secondStored = storedImage("catalog/second", "https://cdn.test/second.jpg");
        List<StoredImage> storedImages = List.of(firstStored, secondStored);
        RuntimeException failure = new RuntimeException("database failed");
        when(imageStorage.upload(any(byte[].class), eq(folder(variantId))))
                .thenReturn(firstStored, secondStored);
        when(commandService.attachImages(productId, variantId, storedImages)).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> imageService.uploadImages(
                        productId,
                        variantId,
                        List.of(imageFile("first.jpg", new byte[]{1}), imageFile("second.jpg", new byte[]{2}))
                )
        );

        assertSame(failure, thrown);
        verify(imageStorage, times(2)).upload(any(byte[].class), eq(folder(variantId)));
        InOrder cleanupOrder = inOrder(imageStorage);
        cleanupOrder.verify(imageStorage).delete(secondStored.publicId());
        cleanupOrder.verify(imageStorage).delete(firstStored.publicId());
    }

    @Test
    void replacesCatalogMetadataThenCleansUpPreviousAsset() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        MockMultipartFile file = imageFile();
        StoredImage stored = storedImage("catalog/new", "https://cdn.test/new.jpg");
        VariantImageResponse expected = response(imageId, stored.url());
        VariantImageReplacementResult replacement =
                new VariantImageReplacementResult("catalog/old", expected);
        when(imageStorage.upload(any(byte[].class), eq(folder(variantId)))).thenReturn(stored);
        when(commandService.replaceImage(productId, variantId, imageId, stored)).thenReturn(replacement);

        VariantImageResponse actual = imageService.replaceImage(productId, variantId, imageId, file);

        assertSame(expected, actual);
        InOrder order = inOrder(commandService, imageStorage);
        order.verify(commandService).ensureImageExists(productId, variantId, imageId);
        order.verify(imageStorage).upload(any(byte[].class), eq(folder(variantId)));
        order.verify(commandService).replaceImage(productId, variantId, imageId, stored);
        order.verify(imageStorage).delete("catalog/old");
    }

    @Test
    void compensatesNewAssetWhenCatalogReplaceFails() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        StoredImage stored = storedImage("catalog/new", "https://cdn.test/new.jpg");
        RuntimeException failure = new RuntimeException("database failed");
        when(imageStorage.upload(any(byte[].class), eq(folder(variantId)))).thenReturn(stored);
        when(commandService.replaceImage(productId, variantId, imageId, stored)).thenThrow(failure);

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> imageService.replaceImage(productId, variantId, imageId, imageFile())
        );

        assertSame(failure, thrown);
        verify(imageStorage).delete(stored.publicId());
        verify(imageStorage, never()).delete("catalog/old");
    }

    @Test
    void successfulReplacementIsNotUndoneWhenOldAssetCleanupFails() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        StoredImage stored = storedImage("catalog/new", "https://cdn.test/new.jpg");
        VariantImageResponse expected = response(imageId, stored.url());
        when(imageStorage.upload(any(byte[].class), eq(folder(variantId)))).thenReturn(stored);
        when(commandService.replaceImage(productId, variantId, imageId, stored))
                .thenReturn(new VariantImageReplacementResult("catalog/old", expected));
        doThrow(new RuntimeException("cloud cleanup failed"))
                .when(imageStorage).delete("catalog/old");

        VariantImageResponse actual = imageService.replaceImage(
                productId,
                variantId,
                imageId,
                imageFile()
        );

        assertSame(expected, actual);
    }

    @Test
    void deletesCatalogRecordBeforeCloudAsset() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        when(commandService.removeImage(productId, variantId, imageId)).thenReturn("catalog/old");

        imageService.deleteImage(productId, variantId, imageId);

        InOrder order = inOrder(commandService, imageStorage);
        order.verify(commandService).removeImage(productId, variantId, imageId);
        order.verify(imageStorage).delete("catalog/old");
    }

    @Test
    void rejectsNonImageBeforeCallingCatalogOrStorage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "not an image".getBytes()
        );

        AppException exception = assertThrows(
                AppException.class,
                () -> imageService.uploadImage(UUID.randomUUID(), UUID.randomUUID(), file)
        );

        assertEquals(CommonErrorCode.UNSUPPORTED_FILE_TYPE, exception.getErrorCode());
        verifyNoInteractions(commandService, imageStorage);
    }

    private MockMultipartFile imageFile() {
        return imageFile("product.jpg", new byte[]{1, 2, 3});
    }

    private MockMultipartFile imageFile(String filename, byte[] content) {
        return new MockMultipartFile("files", filename, "image/jpeg", content);
    }

    @Test
    void missingOrOversizedFilesNeverReachDatabaseOrStorage() {
        var empty = new MockMultipartFile("file", new byte[0]);
        for (var file : java.util.Arrays.asList(null, empty)) {
            var error = assertThrows(AppException.class,
                    () -> imageService.uploadImage(UUID.randomUUID(), UUID.randomUUID(), file));
            assertEquals(CommonErrorCode.FILE_REQUIRED, error.getErrorCode());
        }
        var large = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[5 * 1024 * 1024 + 1]);
        var error = assertThrows(AppException.class,
                () -> imageService.uploadImage(UUID.randomUUID(), UUID.randomUUID(), large));
        assertEquals(CommonErrorCode.FILE_TOO_LARGE, error.getErrorCode());
        verifyNoInteractions(commandService, imageStorage);
    }

    @Test
    void uploadFailureDoesNotAttachMetadata() {
        when(imageStorage.upload(any(), anyString())).thenThrow(new RuntimeException("storage unavailable"));
        var error = assertThrows(AppException.class,
                () -> imageService.uploadImage(UUID.randomUUID(), UUID.randomUUID(), imageFile()));
        assertEquals(CommonErrorCode.FILE_UPLOAD_FAILED, error.getErrorCode());
        verify(commandService, never()).attachImage(any(), any(), any());
        verify(imageStorage, never()).delete(any());
    }

    @Test
    void failedDatabaseRemovalNeverDeletesCloudAsset() {
        doThrow(new org.springframework.dao.OptimisticLockingFailureException("conflict"))
                .when(commandService).removeImage(any(), any(), any());
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
                () -> imageService.deleteImage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        verifyNoInteractions(imageStorage);
    }

    @Test
    void failedCompensationPreservesOriginalDatabaseFailure() {
        var original = new RuntimeException("database conflict");
        var cleanup = new RuntimeException("cleanup unavailable");
        when(imageStorage.upload(any(), anyString())).thenReturn(new StoredImage("new", "https://cdn.test/new"));
        when(commandService.attachImage(any(), any(), any())).thenThrow(original);
        doThrow(cleanup).when(imageStorage).delete("new");
        var error = assertThrows(RuntimeException.class,
                () -> imageService.uploadImage(UUID.randomUUID(), UUID.randomUUID(), imageFile()));
        assertSame(original, error);
        assertSame(cleanup, error.getSuppressed()[0]);
    }

    private StoredImage storedImage(String publicId, String url) {
        return new StoredImage(publicId, url);
    }

    private VariantImageResponse response(UUID id, String url) {
        return new VariantImageResponse(id, url, 0);
    }

    private String folder(UUID variantId) {
        return "rimarket/catalog/variants/" + variantId;
    }
}
