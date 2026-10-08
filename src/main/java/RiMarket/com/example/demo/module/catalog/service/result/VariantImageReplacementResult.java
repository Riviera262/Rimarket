package RiMarket.com.example.demo.module.catalog.service.result;

import RiMarket.com.example.demo.module.catalog.dto.imageVariant.VariantImageResponse;

/** Internal database-command result; never returned directly by a controller. */

public record VariantImageReplacementResult(
        String previousPublicId,
        VariantImageResponse image
) {
}
