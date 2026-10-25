package RiMarket.com.example.demo.module.catalog.api;

import RiMarket.com.example.demo.module.catalog.dto.productVariant.CreateProductVariantRequest;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;

import java.util.UUID;

/** Public command API. DTOs may cross the boundary; managed entities must not. */
public interface CatalogVariantCommands {
    ProductVariantResponse addVariant(UUID productId, CreateProductVariantRequest request);
}
