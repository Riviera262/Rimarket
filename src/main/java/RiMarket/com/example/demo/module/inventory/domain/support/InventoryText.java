package RiMarket.com.example.demo.module.inventory.domain.support;

import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainException;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainViolation;

public final class InventoryText {

    private InventoryText() {}

    public static String requireText(String value, int maxLength, InventoryDomainViolation violation){
        if (value == null || value.isBlank()){
            throw new InventoryDomainException(violation);
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength){
            throw new InventoryDomainException(violation);
        }
        return normalized;
    }

    public static String optionalText(String value, int maxLength, InventoryDomainViolation violation) {
        return value == null ? null : requireText(value, maxLength, violation);
    }
}
