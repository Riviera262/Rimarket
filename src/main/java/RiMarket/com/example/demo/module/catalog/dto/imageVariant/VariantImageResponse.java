package RiMarket.com.example.demo.module.catalog.dto.imageVariant;

import java.util.UUID;

public record VariantImageResponse(
        UUID id,
        String url,
        int sortOrder
) {
}
