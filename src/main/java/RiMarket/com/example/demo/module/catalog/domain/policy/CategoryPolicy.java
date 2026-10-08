package RiMarket.com.example.demo.module.catalog.domain.policy;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.support.CatalogText;
import org.springframework.stereotype.Component;

public final class CategoryPolicy {

    public static final int MAX_TREE_DEPTH = 3;
    public static final int MAX_NAME_LENGTH = 100;

    private CategoryPolicy(){

    }

    public static String normalizeName(String name){
        return CatalogText.requireText(name, MAX_NAME_LENGTH, CatalogDomainViolation.INVALID_CATEGORY_NAME);
    }
}
