package RiMarket.com.example.demo.module.catalog.dto.product;

import java.util.UUID;

public record ProductSummaryResponse(
        UUID productId,
        String name,
        String slug,
        UUID categoryId,
        boolean active
) {
}
