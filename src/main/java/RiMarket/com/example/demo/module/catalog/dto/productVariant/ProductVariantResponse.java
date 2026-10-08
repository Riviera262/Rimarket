package RiMarket.com.example.demo.module.catalog.dto.productVariant;

import RiMarket.com.example.demo.module.catalog.entity.ProductVariant;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        int sortOrder,
        boolean active
){
}
