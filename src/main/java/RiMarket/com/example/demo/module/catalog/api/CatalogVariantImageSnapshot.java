package RiMarket.com.example.demo.module.catalog.api;

import java.util.UUID;

// Public read contract: storage credentials/publicId and JPA entities stay in Catalog.
public record CatalogVariantImageSnapshot(UUID variantId, UUID imageId, String url, int sortOrder) {}
