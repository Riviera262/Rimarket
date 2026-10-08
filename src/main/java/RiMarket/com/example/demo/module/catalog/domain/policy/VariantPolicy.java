package RiMarket.com.example.demo.module.catalog.domain.policy;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.support.CatalogText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VariantPolicy {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_SKU_LENGTH = 100;

    private VariantPolicy() {

    }

    public static String normalizeName(String name){
        return CatalogText.requireText(name, MAX_NAME_LENGTH, CatalogDomainViolation.INVALID_VARIANT_NAME);
    }

    public static String normalizeSku(String sku){
        return CatalogText.requireText(sku, MAX_SKU_LENGTH, CatalogDomainViolation.INVALID_SKU);
    }

    public static int validateSortOrder(int sortOrder) {
        if (sortOrder < 0) {
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_ORDER);
        }

        return sortOrder;
    }

    public static void validateReorder(Set<UUID> currentVariantIds, List<UUID> orderedVariantIds) {
        if (orderedVariantIds == null || orderedVariantIds.size() != currentVariantIds.size()){
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_ORDER);
        }

        Set<UUID> requestedVariantIds = new HashSet<>(orderedVariantIds);

        boolean hasDuplicate = requestedVariantIds.size() != orderedVariantIds.size();
        boolean hasMissingOneForeignId = !requestedVariantIds.equals(currentVariantIds);

        if (hasDuplicate || hasMissingOneForeignId) {
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_ORDER);
        }
    }
}
