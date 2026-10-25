package RiMarket.com.example.demo.module.inventory.dto;

import java.util.UUID;

public record InventoryItemSummaryResponse(
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        int onHandQty,
        int reservedQty,
        int availableQty,
        Long version,
        String thumbnailUrl
) {}
