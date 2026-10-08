package RiMarket.com.example.demo.module.catalog.mapper;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.policy.CategoryPolicy;
import RiMarket.com.example.demo.module.catalog.dto.category.CategoryResponse;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class CategoryMapper {
    public CategoryResponse toResponse(Category category){
        if (category == null){
            return null;
        }

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getParent() != null ? category.getParent().getId() : null,
                List.of());
    }

    public CategoryResponse toTreeResponse(Category category){
        return toTreeResponse(
                category,
                CategoryPolicy.MAX_TREE_DEPTH,
                new HashSet<>()
        );
    }

    private CategoryResponse toTreeResponse(Category category, int remainingDepth, Set<UUID> visited){
        if (!visited.add(category.getId())){
            throw new CatalogDomainException(CatalogDomainViolation.CATEGORY_CIRCULAR_DEPENDENCY);
        }

        List<CategoryResponse> children = List.of();

        if (remainingDepth > 1){
            children = category.getSubCategories().stream()
                    .map(
                            child -> toTreeResponse(child, remainingDepth - 1, visited)
                    )
                    .toList();
        }

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getParent() != null ? category.getParent().getId() : null,
                children
        );
    }

}
