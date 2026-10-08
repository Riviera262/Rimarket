package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.policy.CategoryHierarchyPolicy;
import RiMarket.com.example.demo.module.catalog.domain.policy.CategoryPolicy;
import RiMarket.com.example.demo.module.catalog.dto.category.CategoryResponse;
import RiMarket.com.example.demo.module.catalog.dto.category.CreateCategoryRequest;
import RiMarket.com.example.demo.module.catalog.dto.category.UpdateCategoryRequest;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import RiMarket.com.example.demo.module.catalog.exception.CatalogErrorCode;
import RiMarket.com.example.demo.module.catalog.mapper.CategoryMapper;
import RiMarket.com.example.demo.module.catalog.repository.CategoryRepository;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import RiMarket.com.example.demo.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;
    private final CategoryHierarchyPolicy categoryHierarchyPolicy;

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(UUID categoryId){
        Category category = getCategory(categoryId, CatalogErrorCode.CATEGORY_NOT_FOUND);

        return categoryMapper.toResponse(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryTree(){
        List<Category> categories = categoryRepository.findByParentIsNull();

        return categories.stream()
                .map(categoryMapper::toTreeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getChildren(UUID parentId){
        if (!categoryRepository.existsById(parentId)){
            throw new AppException(CatalogErrorCode.CATEGORY_NOT_FOUND);
        }

        List<Category> categories = categoryRepository.findByParent_Id(parentId);

        return categories.stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CategoryResponse createCategory(CreateCategoryRequest request){
        String name = CategoryPolicy.normalizeName(request.name());

        Category parent = null;

        if (request.parentId() != null){
            parent = getCategory(request.parentId(), CatalogErrorCode.PARENT_CATEGORY_NOT_FOUND);
        }

        categoryHierarchyPolicy.validateCreateUnder(parent);
        validateSiblingName(name, parent, null);

        Category category = new Category(name, parent);
        Category saved = categoryRepository.save(category);

        return categoryMapper.toResponse(saved);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request){
        Category category = getCategory(categoryId, CatalogErrorCode.CATEGORY_NOT_FOUND);

        String newName = CategoryPolicy.normalizeName(request.name());

        Category newParent = null;

        if (request.parentId() != null){
            newParent = getCategory(request.parentId(), CatalogErrorCode.PARENT_CATEGORY_NOT_FOUND);
        }

        // Moving a populated category to level 1 would make its existing Products invalid.
        if (newParent == null && productRepository.existsByCategoryId(categoryId)) {
            throw new CatalogDomainException(CatalogDomainViolation.PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT);
        }

        categoryHierarchyPolicy.validateMove(category , newParent);

        validateSiblingName(newName, newParent, categoryId);

        category.rename(newName);
        category.moveTo(newParent);

        return categoryMapper.toResponse(category);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void deleteCategory(UUID categoryId){
        Category category = getCategory(categoryId, CatalogErrorCode.CATEGORY_NOT_FOUND);

        if (categoryRepository.existsByParent_Id(categoryId)){
            throw new CatalogDomainException(CatalogDomainViolation.CATEGORY_HAS_CHILDREN);
        }

        if (productRepository.existsByCategoryId(categoryId)){
            throw new CatalogDomainException(CatalogDomainViolation.CATEGORY_HAS_PRODUCTS);
        }

        categoryRepository.delete(category);
    }

    private void validateSiblingName(String name, Category parent, UUID excludeCategoryId) {
        boolean exists;

        if (parent == null) {

            exists = excludeCategoryId == null
                    ? categoryRepository.existsByParentIsNullAndNameIgnoreCase(name)
                    : categoryRepository.existsByParentIsNullAndNameIgnoreCaseAndIdNot(name, excludeCategoryId);

        } else {

            exists = excludeCategoryId == null
                    ? categoryRepository.existsByParent_IdAndNameIgnoreCase(parent.getId(), name)
                    : categoryRepository.existsByParent_IdAndNameIgnoreCaseAndIdNot(parent.getId(), name, excludeCategoryId);
        }

        if (exists) {
            throw new CatalogDomainException(CatalogDomainViolation.CATEGORY_DUPLICATE_SIBLING_NAME);
        }
    }

    private Category getCategory(UUID categoryId, CatalogErrorCode notFoundError) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(notFoundError));
    }
}
