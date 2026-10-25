package RiMarket.com.example.demo.module.inventory.dto;

import java.util.UUID;

public record InventoryImageResponse(UUID imageId, String url, int sortOrder) {}
