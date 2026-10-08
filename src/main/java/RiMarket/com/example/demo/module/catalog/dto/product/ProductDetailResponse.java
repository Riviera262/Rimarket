package RiMarket.com.example.demo.module.catalog.dto.product;

import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantDetailResponse;

import java.util.List;
import java.util.UUID;

public record ProductDetailResponse(
        UUID id,
        String name,
        String slug,
        String description,
        UUID categoryId,
        boolean active,
        List<ProductVariantDetailResponse> variants
) {
}
