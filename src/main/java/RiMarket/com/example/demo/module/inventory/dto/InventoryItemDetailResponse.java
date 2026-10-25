package RiMarket.com.example.demo.module.inventory.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record InventoryItemDetailResponse(
        UUID variantId,
        String sku,
        String productName,
        String variantName,
        int onHandQty,
        int reservedQty,
        int availableQty,
        Long version,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        List<InventoryImageResponse> images
) {}
