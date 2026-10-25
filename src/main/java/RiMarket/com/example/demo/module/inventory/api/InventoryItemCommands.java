package RiMarket.com.example.demo.module.inventory.api;

import RiMarket.com.example.demo.module.inventory.domain.InventoryActor;
import RiMarket.com.example.demo.module.inventory.dto.InventoryItemResponse;

import java.util.UUID;

/** Commands for application workflows; no Inventory entities or repositories are exposed. */
public interface InventoryItemCommands {
    InventoryItemResponse createItemWithStock(UUID variantId, int initialQuantity, UUID operationId, InventoryActor actor);
}
