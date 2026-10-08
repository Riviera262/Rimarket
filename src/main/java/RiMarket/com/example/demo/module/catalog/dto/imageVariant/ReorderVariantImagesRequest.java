package RiMarket.com.example.demo.module.catalog.dto.imageVariant;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderVariantImagesRequest(
        @NotNull(message = "Image ids are required")
        List<@NotNull(message = "Image id is required") UUID> imageIds
) {
}
