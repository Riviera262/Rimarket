package RiMarket.com.example.demo.module.catalog.dto.productVariant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductVariantRequest(
    @NotBlank(message = "Variant name is required")
    @Size(max = 255, message = "name must not exceed 255 characters")
    String name,

    @NotNull(message = "Variant price is required")
    @PositiveOrZero
    BigDecimal price
) {
}
