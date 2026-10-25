package RiMarket.com.example.demo.module.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReceiveStockRequest(
        @NotNull UUID operationId,
        @Positive int quantity,
        @Size(max = 1000) String reason,
        @Size(max = 50) String referenceType,
        UUID referenceId
) {}
