package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.api.CatalogVariantLookup;
import RiMarket.com.example.demo.module.catalog.api.CatalogVariantSnapshot;
import RiMarket.com.example.demo.module.catalog.api.CatalogVariantImageSnapshot;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogVariantLookupService implements CatalogVariantLookup {
    private final ProductRepository productRepository;

    @Override
    public Optional<CatalogVariantSnapshot> findVariant(UUID variantId) {
        return productRepository.findVariantSnapshot(variantId);
    }

    @Override
    public List<CatalogVariantImageSnapshot> findImages(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        return productRepository.findVariantImageSnapshots(variantIds);
    }
}
