package RiMarket.com.example.demo.module.catalog.dto.productVariant;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReorderProductVariantsRequest(
        @NotNull(message = "Variant ids are required")
        List<@NotNull(message = "Variant id is required") UUID> variantIds
) {
}
