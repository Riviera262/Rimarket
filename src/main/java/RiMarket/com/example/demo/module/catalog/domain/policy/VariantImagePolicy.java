package RiMarket.com.example.demo.module.catalog.domain.policy;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.support.CatalogText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VariantImagePolicy {

    public static final int MAX_IMAGES = 8;

    public static final int MAX_PUBLIC_ID_LENGTH = 255;
    public static final int MAX_URL_LENGTH = 500;


    private VariantImagePolicy() {
    }

    public static String normalizePublicId(String publicId) {
        return CatalogText.requireText(publicId, MAX_PUBLIC_ID_LENGTH, CatalogDomainViolation.INVALID_VARIANT_IMAGE_PUBLIC_ID);
    }


    public static String normalizeUrl(String url) {
        return CatalogText.requireText(url, MAX_URL_LENGTH, CatalogDomainViolation.INVALID_VARIANT_IMAGE_URL);
    }


    public static int validateSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_IMAGE_SORT_ORDER);
        }

        return sortOrder;
    }


    public static void validateReorder(Set<UUID> currentImageIds, List<UUID> orderedImageIds) {
        if (orderedImageIds == null || orderedImageIds.size() != currentImageIds.size()) {
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_IMAGE_ORDER);
        }

        Set<UUID> requestedIds = new HashSet<>(orderedImageIds);

        // duplicate ID
        if (requestedIds.size() != orderedImageIds.size()) {
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_IMAGE_ORDER);
        }

        // missing / foreign IDs
        if (!requestedIds.equals(currentImageIds)) {
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_IMAGE_ORDER);
        }
    }
}