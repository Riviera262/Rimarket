package RiMarket.com.example.demo.module.catalog.dto.productVariant;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductVariantRequest(
        @Size(max = 255, message = "name must not exceed 255 characters")
        @NotBlank(message = "Variant name is required")
        String name,

        @NotNull(message = "Price is required")
        @PositiveOrZero(message = "Price cannot be negative")
        BigDecimal price
) {

}
