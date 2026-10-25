package RiMarket.com.example.demo.module.inventory.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateInventoryItemRequest(@NotNull UUID variantId) {}
