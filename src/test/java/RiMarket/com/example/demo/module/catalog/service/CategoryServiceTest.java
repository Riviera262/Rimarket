package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.policy.CategoryHierarchyPolicy;
import RiMarket.com.example.demo.module.catalog.dto.category.CategoryResponse;
import RiMarket.com.example.demo.module.catalog.dto.category.CreateCategoryRequest;
import RiMarket.com.example.demo.module.catalog.dto.category.UpdateCategoryRequest;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import RiMarket.com.example.demo.module.catalog.mapper.CategoryMapper;
import RiMarket.com.example.demo.module.catalog.repository.CategoryRepository;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(
                categoryRepository,
                productRepository,
                new CategoryMapper(),
                new CategoryHierarchyPolicy()
        );
    }

    @Test
    void createsNormalizedChildUnderExistingParent() {
        UUID parentId = UUID.randomUUID();
        Category parent = category("Electronics", null, parentId);

        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByParent_IdAndNameIgnoreCase(parentId, "Phones"))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory(
                new CreateCategoryRequest("  Phones  ", parentId)
        );

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertEquals("Phones", captor.getValue().getName());
        assertSame(parent, captor.getValue().getParent());
        assertEquals(parentId, response.parentId());
    }

    @Test
    void rejectsDuplicateSiblingBeforeSaving() {
        UUID parentId = UUID.randomUUID();
        Category parent = category("Electronics", null, parentId);

        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByParent_IdAndNameIgnoreCase(parentId, "Phones"))
                .thenReturn(true);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> categoryService.createCategory(new CreateCategoryRequest("Phones", parentId))
        );

        assertEquals(CatalogDomainViolation.CATEGORY_DUPLICATE_SIBLING_NAME, exception.getViolation());
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void updatesNameAndMovesCategoryToNewParent() {
        UUID categoryId = UUID.randomUUID();
        UUID oldParentId = UUID.randomUUID();
        UUID newParentId = UUID.randomUUID();
        Category oldParent = category("Old parent", null, oldParentId);
        Category newParent = category("New parent", null, newParentId);
        Category category = category("Old name", oldParent, categoryId);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findById(newParentId)).thenReturn(Optional.of(newParent));
        when(categoryRepository.existsByParent_IdAndNameIgnoreCaseAndIdNot(
                newParentId,
                "New name",
                categoryId
        )).thenReturn(false);

        CategoryResponse response = categoryService.updateCategory(
                categoryId,
                new UpdateCategoryRequest("  New name ", newParentId)
        );

        assertEquals("New name", category.getName());
        assertSame(newParent, category.getParent());
        assertEquals(newParentId, response.parentId());
    }

    @Test
    void rejectsMovingCategoryWithProductsToRoot() {
        UUID categoryId = UUID.randomUUID();
        Category parent = category("Electronics", null, UUID.randomUUID());
        Category category = category("Laptops", parent, categoryId);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(categoryId)).thenReturn(true);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> categoryService.updateCategory(
                        categoryId,
                        new UpdateCategoryRequest("Laptops", null)
                )
        );

        assertEquals(CatalogDomainViolation.PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT, exception.getViolation());
        assertSame(parent, category.getParent());
    }

    @Test
    void refusesToDeleteCategoryWithChildren() {
        UUID categoryId = UUID.randomUUID();
        Category category = category("Electronics", null, categoryId);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParent_Id(categoryId)).thenReturn(true);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> categoryService.deleteCategory(categoryId)
        );

        assertEquals(CatalogDomainViolation.CATEGORY_HAS_CHILDREN, exception.getViolation());
        verify(productRepository, never()).existsByCategoryId(categoryId);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void refusesToDeleteCategoryContainingProducts() {
        UUID categoryId = UUID.randomUUID();
        Category category = category("Electronics", null, categoryId);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParent_Id(categoryId)).thenReturn(false);
        when(productRepository.existsByCategoryId(categoryId)).thenReturn(true);

        CatalogDomainException exception = assertThrows(
                CatalogDomainException.class,
                () -> categoryService.deleteCategory(categoryId)
        );

        assertEquals(CatalogDomainViolation.CATEGORY_HAS_PRODUCTS, exception.getViolation());
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    private Category category(String name, Category parent, UUID id) {
        Category category = new Category(name, parent);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
