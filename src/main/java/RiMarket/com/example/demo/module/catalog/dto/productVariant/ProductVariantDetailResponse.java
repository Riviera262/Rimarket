package RiMarket.com.example.demo.module.catalog.dto.productVariant;

import RiMarket.com.example.demo.module.catalog.dto.imageVariant.VariantImageResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductVariantDetailResponse(
        UUID id,
        String sku,
        String name,
        BigDecimal price,
        int sortOrder,
        boolean active,
        List<VariantImageResponse> images
) {
}
