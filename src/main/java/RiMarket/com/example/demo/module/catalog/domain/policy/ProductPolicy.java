package RiMarket.com.example.demo.module.catalog.domain.policy;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.support.CatalogText;

public final class ProductPolicy {

    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_SLUG_LENGTH = 180;

    private ProductPolicy() {

    }

    public static String normalizeName(String name) {
        return CatalogText.requireText(name, MAX_NAME_LENGTH, CatalogDomainViolation.INVALID_PRODUCT_NAME);
    }

    public static String normalizeSlug(String slug) {
        return CatalogText.requireText(slug, MAX_SLUG_LENGTH, CatalogDomainViolation.INVALID_PRODUCT_SLUG);
    }
}
