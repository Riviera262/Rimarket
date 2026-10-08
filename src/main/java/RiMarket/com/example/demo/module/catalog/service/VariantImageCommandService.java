package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.service.result.VariantImageReplacementResult;
import RiMarket.com.example.demo.module.catalog.dto.imageVariant.VariantImageResponse;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.entity.VariantImage;
import RiMarket.com.example.demo.module.catalog.exception.CatalogErrorCode;
import RiMarket.com.example.demo.module.catalog.mapper.VariantImageMapper;
import RiMarket.com.example.demo.module.catalog.port.StoredImage;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import RiMarket.com.example.demo.shared.exception.AppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns the database transaction for VariantImage commands. Keeping this in a
 * separate bean lets the Cloudinary-facing service observe transaction commit
 * failures and compensate an upload that could not be attached to the aggregate.
 */
@Service
@RequiredArgsConstructor
public class VariantImageCommandService {
    @PersistenceContext
    private EntityManager entityManager;

    private final ProductRepository productRepository;
    private final VariantImageMapper variantImageMapper;

    //Check image size
    @Transactional(readOnly = true)
    public void ensureCanAddImage(UUID productId, UUID variantId) {
        ensureCanAddImages(productId, variantId, 1);
    }

    @Transactional(readOnly = true)
    public void ensureCanAddImages(UUID productId, UUID variantId, int imageCount) {
        Product product = loadAggregateWithImages(productId);
        product.ensureCanAddVariantImages(variantId, imageCount);
    }

    //Check if image do exists
    @Transactional(readOnly = true)
    public void ensureImageExists(UUID productId, UUID variantId, UUID imageId) {
        Product product = loadAggregateWithImages(productId);
        product.getVariantImage(variantId, imageId);
    }


    @Transactional(readOnly = true)
    public List<VariantImageResponse> getImages(UUID productId, UUID variantId) {
        Product product = loadAggregateWithImages(productId);

        return variantImageMapper.toResponseList(product.getVariantImages(variantId));
    }

    @Transactional
    public VariantImageResponse attachImage(
            UUID productId,
            UUID variantId,
            StoredImage storedImage
    ) {
        Product product = loadAggregateWithImagesForCommand(productId);
        VariantImage image = product.addVariantImage(
                variantId,
                storedImage.publicId(),
                storedImage.url()
        );

        productRepository.flush();

        return variantImageMapper.toResponse(image);
    }

    @Transactional
    public List<VariantImageResponse> attachImages(
            UUID productId,
            UUID variantId,
            List<StoredImage> storedImages
    ) {
        if (storedImages == null || storedImages.isEmpty()) {
            throw new IllegalArgumentException("Stored images are required");
        }

        Product product = loadAggregateWithImagesForCommand(productId);
        product.ensureCanAddVariantImages(variantId, storedImages.size());

        List<VariantImage> images = storedImages.stream()
                .map(storedImage -> product.addVariantImage(
                        variantId,
                        storedImage.publicId(),
                        storedImage.url()
                ))
                .toList();

        productRepository.flush();

        return variantImageMapper.toResponseList(images);
    }

    @Transactional
    public VariantImageReplacementResult replaceImage(
            UUID productId,
            UUID variantId,
            UUID imageId,
            StoredImage storedImage
    ) {
        Product product = loadAggregateWithImagesForCommand(productId);
        String previousPublicId = product.getVariantImage(variantId, imageId).getPublicId();
        VariantImage image = product.replaceVariantImage(
                variantId,
                imageId,
                storedImage.publicId(),
                storedImage.url()
        );

        productRepository.flush();

        return new VariantImageReplacementResult(previousPublicId, variantImageMapper.toResponse(image));
    }

    @Transactional
    public String removeImage(UUID productId, UUID variantId, UUID imageId) {
        Product product = loadAggregateWithImagesForCommand(productId);
        VariantImage removedImage = product.removeVariantImage(variantId, imageId);

        return removedImage.getPublicId();
    }


    @Transactional
    public void reorderImages(UUID productId, UUID variantId, List<UUID> orderedImageIds) {
        Product product = loadAggregateWithImagesForCommand(productId);

        product.reorderVariantImages(variantId, orderedImageIds);
    }

    private Product loadAggregateWithImages(UUID productId) {
        Product product = productRepository.findAggregateById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND));

        // Same persistence context: hydrate images on Product's managed variants.
        productRepository.findAllWithImagesByProductId(productId);
        return product;

    }

    //2 queries to get full product
    private Product loadAggregateWithImagesForCommand(UUID productId) {
        Product product = productRepository.findAggregateForChildCommandById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND));

        entityManager.lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        productRepository.findAllWithImagesByProductId(productId);
        return product;
    }
}
