package RiMarket.com.example.demo.module.inventory.mapper;

import RiMarket.com.example.demo.module.catalog.api.CatalogVariantImageSnapshot;
import RiMarket.com.example.demo.module.inventory.dto.*;
import RiMarket.com.example.demo.module.inventory.entity.InventoryItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryItemMapper {

    public InventoryItemResponse toResponse(InventoryItem item){
        if (item == null){
            return null;
        }

        return new InventoryItemResponse(
                item.getVariantId(),
                item.getSku(),
                item.getProductName(),
                item.getVariantName(),
                item.getOnHandQty(),
                item.getReservedQty(),
                item.getAvailableQty(),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    // One summary per row; Page.map(...) in the service preserves pagination metadata.
    public InventoryItemSummaryResponse toSummaryResponse(InventoryItem item, String thumbnailUrl){

        return new InventoryItemSummaryResponse(
                item.getVariantId(),
                item.getSku(),
                item.getProductName(),
                item.getVariantName(),
                item.getOnHandQty(),
                item.getReservedQty(),
                item.getAvailableQty(),
                item.getVersion(),
                thumbnailUrl
        );
    }

    // All database/API reads have already happened in the service, not in this mapper.
    public InventoryItemDetailResponse toDetailResponse(InventoryItem item, List<CatalogVariantImageSnapshot> images) {

        List<InventoryImageResponse> imageResponses = images.stream()
                .map(image -> new InventoryImageResponse(image.imageId(), image.url(), image.sortOrder()))
                .toList();

        return new InventoryItemDetailResponse(
                item.getVariantId(),
                item.getSku(),
                item.getProductName(),
                item.getVariantName(),
                item.getOnHandQty(),
                item.getReservedQty(),
                item.getAvailableQty(),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                imageResponses
        );
    }
}
