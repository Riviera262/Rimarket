package RiMarket.com.example.demo.module.inventory.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record InventoryItemResponse(
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        int onHandQty,
        int reservedQty,
        int availableQty,
        Long version,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {

}
