package RiMarket.com.example.demo.module.catalog.api;

import java.util.UUID;

/** Read-only snapshot for Inventory initialization; not an event or HTTP create request. */
public record CatalogVariantSnapshot(UUID variantId, String sku, String productName, String variantName) {}
