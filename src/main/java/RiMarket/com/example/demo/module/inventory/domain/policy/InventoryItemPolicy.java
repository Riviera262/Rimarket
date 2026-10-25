package RiMarket.com.example.demo.module.inventory.domain.policy;

import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainViolation;
import RiMarket.com.example.demo.module.inventory.domain.support.InventoryText;

public class InventoryItemPolicy {
    private static final int MAX_SKU_LENGTH = 100;
    private static final int MAX_NAME_LENGTH = 255;

    private InventoryItemPolicy(){}

    public static String normalizeSku(String sku){
        return InventoryText.requireText(sku, MAX_SKU_LENGTH, InventoryDomainViolation.INVALID_METADATA);
    }

    public static String normalizeName(String productName){
        return InventoryText.requireText(productName, MAX_NAME_LENGTH, InventoryDomainViolation.INVALID_METADATA);
    }

}
