package RiMarket.com.example.demo.module.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AdjustStockRequest(
        @NotNull UUID operationId,
        int delta,
        @NotBlank @Size(max = 1000) String reason,
        @Size(max = 50) String referenceType,
        UUID referenceId
) {}
