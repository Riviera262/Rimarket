package RiMarket.com.example.demo.module.catalog.mapper;

import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantDetailResponse;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.catalog.entity.ProductVariant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductVariantMapper {

    private final VariantImageMapper variantImageMapper;

    public ProductVariantResponse toResponse(ProductVariant variant) {
        if (variant == null) {
            return null;
        }
        return new ProductVariantResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getSortOrder(),
                variant.isActive()
        );
    }

    public ProductVariantDetailResponse toDetailResponse(ProductVariant variant) {
        if (variant == null) {
            return null;
        }

        return new ProductVariantDetailResponse(
                variant.getId(),
                variant.getSku(),
                variant.getName(),
                variant.getPrice(),
                variant.getSortOrder(),
                variant.isActive(),
                variantImageMapper.toResponseList(variant.getImages())
        );
    }
}
