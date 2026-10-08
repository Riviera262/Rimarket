package RiMarket.com.example.demo.module.catalog.domain.support;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;

public final class CatalogText {

    private CatalogText() {}

    public static String requireText(String value, int maxLength, CatalogDomainViolation violation) {
        if (value == null || value.isBlank()) {
            throw new CatalogDomainException(violation);
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new CatalogDomainException(violation);
        }

        return normalized;
    }
}