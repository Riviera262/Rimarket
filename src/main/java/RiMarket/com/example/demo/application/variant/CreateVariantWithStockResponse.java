package RiMarket.com.example.demo.application.variant;

import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.inventory.dto.InventoryItemResponse;

public record CreateVariantWithStockResponse(
        ProductVariantResponse variant,
        InventoryItemResponse inventory
) {}
