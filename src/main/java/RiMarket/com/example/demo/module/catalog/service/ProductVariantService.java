package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.domain.identifier.SkuGenerator;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.CreateProductVariantRequest;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ReorderProductVariantsRequest;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.UpdateProductVariantRequest;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.entity.ProductVariant;
import RiMarket.com.example.demo.module.catalog.exception.CatalogErrorCode;
import RiMarket.com.example.demo.module.catalog.mapper.ProductVariantMapper;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import RiMarket.com.example.demo.shared.exception.AppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductVariantService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ProductVariantMapper productVariantMapper;
    private final ProductRepository productRepository;
    private final SkuGenerator skuGenerator;

    @Transactional
    public ProductVariantResponse addVariant(UUID productId, CreateProductVariantRequest request){
        Product product = loadProductAggregate(productId);

        String sku = skuGenerator.generate(product.getName(), request.name());

        if(productRepository.existsByVariantsSkuIgnoreCase(sku)){
            throw new AppException(CatalogErrorCode.SKU_ALREADY_EXISTS);
        }

        ProductVariant variant = product.addVariant(sku, request.name(), request.price());
        productRepository.flush();

        return productVariantMapper.toResponse(variant);
    }

    @Transactional
    public ProductVariantResponse updateVariant(UUID productId, UUID variantId, UpdateProductVariantRequest request){
        Product product = loadProductAggregate(productId);

        ProductVariant variant = product.updateVariant(variantId, request.name(), request.price());

        return productVariantMapper.toResponse(variant);
    }

    @Transactional
    public void activateVariant(UUID productId, UUID variantId){
        Product product = loadProductAggregate(productId);

        product.activateVariant(variantId);
    }

    @Transactional
    public void deactivateVariant(UUID productId, UUID variantId){
        Product product = loadProductAggregate(productId);

        product.deactivateVariant(variantId);
    }

    @Transactional
    public void reorder(UUID productId, ReorderProductVariantsRequest request){
        Product product = loadProductAggregate(productId);

        product.reorderVariants(request.variantIds());
    }

    // Fetch the aggregate, then lock only its versioned Product root.
    private Product loadProductAggregate(UUID productId){
        Product product = productRepository.findAggregateForChildCommandById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND));

        entityManager.lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return product;
    }

}
