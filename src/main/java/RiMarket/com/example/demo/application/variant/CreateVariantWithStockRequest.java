package RiMarket.com.example.demo.application.variant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateVariantWithStockRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotNull @PositiveOrZero Integer initialQuantity,
        @NotNull UUID operationId
) {}
