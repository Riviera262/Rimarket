package RiMarket.com.example.demo.module.catalog.mapper;

import RiMarket.com.example.demo.module.catalog.dto.product.ProductDetailResponse;
import RiMarket.com.example.demo.module.catalog.dto.product.ProductSummaryResponse;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantDetailResponse;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.entity.ProductVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductMapper {

    private final ProductVariantMapper productVariantMapper;

    //List of products
    public ProductSummaryResponse toSummaryResponse(Product product){

        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getCategory().getId(),
                product.isActive()
        );
    }

    //Product with full variants detail
    public ProductDetailResponse toDetailResponse(Product product){
        List<ProductVariantDetailResponse> variants = product.getVariants().stream()
                .filter(ProductVariant::isActive)
                .map(productVariantMapper::toDetailResponse)
                .toList();

        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getCategory().getId(),
                product.isActive(),
                variants
        );
    }

}
