package RiMarket.com.example.demo.module.catalog.domain.policy;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class CategoryHierarchyPolicy {

    public void validateCreateUnder(Category parent) {
        if (parent == null) {
            return;
        }

        int parentDepth = calculateDepth(parent);

        if (parentDepth + 1 > CategoryPolicy.MAX_TREE_DEPTH) {
            throw new CatalogDomainException(
                    CatalogDomainViolation.CATEGORY_DEPTH_EXCEEDED
            );
        }
    }

    public void validateMove(Category category, Category newParent){
        int parentDepth = 0;

        if (newParent != null){
            validateNoCycle(category, newParent);
            parentDepth = calculateDepth(newParent);
        }

        int subtreeHeight = calculateSubtreeHeight(category);

        if (parentDepth + subtreeHeight > CategoryPolicy.MAX_TREE_DEPTH){
            throw new CatalogDomainException(CatalogDomainViolation.CATEGORY_DEPTH_EXCEEDED);
        }
    }

    private void validateNoCycle(Category category, Category newParent){
        Category current = newParent;

        while (current != null){
            boolean samePersistedCategory = category.getId() != null
                    && Objects.equals(current.getId(), category.getId());

            if (current == category || samePersistedCategory){
                throw new CatalogDomainException(CatalogDomainViolation.CATEGORY_CIRCULAR_DEPENDENCY);
            }
            current = current.getParent();

        }
    }

    private int calculateDepth(Category category) {
        int depth = 1;

        Category current = category;

        while (current.getParent() != null){
            depth++;
            current = current.getParent();
        }

        return depth;
    }



    private int calculateSubtreeHeight(Category category) {
        if (category.getSubCategories().isEmpty()) {
            return 1;
        }

        return 1 + category
                .getSubCategories()
                .stream()
                .mapToInt(
                        this::calculateSubtreeHeight
                )
                .max()
                .orElse(0);
    }



}
