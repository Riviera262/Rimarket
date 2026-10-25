package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.service.result.VariantImageReplacementResult;
import RiMarket.com.example.demo.module.catalog.dto.imageVariant.VariantImageResponse;
import RiMarket.com.example.demo.module.catalog.port.ImageStorage;
import RiMarket.com.example.demo.module.catalog.port.StoredImage;
import RiMarket.com.example.demo.shared.exception.AppException;
import RiMarket.com.example.demo.shared.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NEVER)
public class VariantImageService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final ImageStorage imageStorage;
    private final VariantImageCommandService variantImageCommandService;

    public List<VariantImageResponse> getImages(UUID productId, UUID variantId) {
        return variantImageCommandService.getImages(productId, variantId);
    }

    public List<VariantImageResponse> uploadImages(
            UUID productId,
            UUID variantId,
            List<MultipartFile> files
    ) {
        validateFiles(files);
        variantImageCommandService.ensureCanAddImages(productId, variantId, files.size());

        List<StoredImage> storedImages = new ArrayList<>(files.size());

        try {
            for (MultipartFile file : files) {
                storedImages.add(uploadToStorage(variantId, file));
            }

            return variantImageCommandService.attachImages(productId, variantId, storedImages);
        } catch (RuntimeException exception) {
            compensateUploads(storedImages, exception);
            throw exception;
        }
    }

    public VariantImageResponse replaceImage(
            UUID productId,
            UUID variantId,
            UUID imageId,
            MultipartFile file
    ) {
        validateFile(file);
        variantImageCommandService.ensureImageExists(productId, variantId, imageId);

        StoredImage storedImage = uploadToStorage(variantId, file);
        VariantImageReplacementResult replacement;

        try {
            replacement = variantImageCommandService.replaceImage(
                    productId,
                    variantId,
                    imageId,
                    storedImage
            );
        } catch (RuntimeException exception) {
            compensateUpload(storedImage.publicId(), exception);
            throw exception;
        }

        cleanupReplacedImage(replacement.previousPublicId(), storedImage.publicId());
        return replacement.image();
    }

    public void deleteImage(UUID productId, UUID variantId, UUID imageId) {
        String publicId = variantImageCommandService.removeImage(productId, variantId, imageId);

        try {
            imageStorage.delete(publicId);
        } catch (RuntimeException exception) {
            log.error("Variant image was removed from Catalog, but Cloudinary cleanup failed for publicId={}", publicId, exception);
            throw new AppException(CommonErrorCode.FILE_DELETE_FAILED);
        }
    }

    public void reorderImages(UUID productId, UUID variantId, List<UUID> orderedImageIds) {
        variantImageCommandService.reorderImages(productId, variantId, orderedImageIds);
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new AppException(CommonErrorCode.FILE_REQUIRED);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(CommonErrorCode.FILE_TOO_LARGE);
        }

        String contentType = file.getContentType();

        if (contentType == null || !contentType.startsWith("image/")) {
            throw new AppException(CommonErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    private void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new AppException(CommonErrorCode.FILE_REQUIRED);
        }

        files.forEach(this::validateFile);
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new AppException(CommonErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private StoredImage uploadToStorage(UUID variantId, MultipartFile file) {
        byte[] fileContent = readFile(file);
        String folder = "rimarket/catalog/variants/" + variantId;

        try {
            return imageStorage.upload(fileContent, folder);
        } catch (RuntimeException exception) {
            throw new AppException(CommonErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    private void compensateUpload(String publicId, RuntimeException originalException) {
        try {
            imageStorage.delete(publicId);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
            log.error("Failed to compensate Cloudinary upload for publicId={}", publicId, compensationException);
        }
    }

    private void compensateUploads(List<StoredImage> storedImages, RuntimeException originalException) {
        for (int index = storedImages.size() - 1; index >= 0; index--) {
            compensateUpload(storedImages.get(index).publicId(), originalException);
        }
    }

    private void cleanupReplacedImage(String previousPublicId, String currentPublicId) {
        if (Objects.equals(previousPublicId, currentPublicId)) {
            return;
        }

        try {
            imageStorage.delete(previousPublicId);
        } catch (RuntimeException exception) {
            log.error(
                    "Variant image was replaced, but cleanup of the previous Cloudinary asset failed for publicId={}",
                    previousPublicId,
                    exception
            );
        }
    }


}
