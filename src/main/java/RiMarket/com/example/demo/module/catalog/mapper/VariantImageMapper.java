package RiMarket.com.example.demo.module.catalog.mapper;

import RiMarket.com.example.demo.module.catalog.dto.imageVariant.VariantImageResponse;
import RiMarket.com.example.demo.module.catalog.entity.VariantImage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VariantImageMapper {

    public VariantImageResponse toResponse(VariantImage image) {

        return new VariantImageResponse(
                image.getId(),
                image.getUrl(),
                image.getSortOrder()
        );
    }

    public List<VariantImageResponse> toResponseList(List<VariantImage> images){

        return images.stream()
                .map(this::toResponse)
                .toList();
    }
}