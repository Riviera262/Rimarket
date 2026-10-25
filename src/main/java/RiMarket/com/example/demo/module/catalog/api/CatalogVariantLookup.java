package RiMarket.com.example.demo.module.catalog.api;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CatalogVariantLookup {

    Optional<CatalogVariantSnapshot> findVariant(UUID variantId);

    // Includes inactive variants. Results are ordered by variantId, sortOrder, imageId.
    List<CatalogVariantImageSnapshot> findImages(Collection<UUID> variantIds);
}
