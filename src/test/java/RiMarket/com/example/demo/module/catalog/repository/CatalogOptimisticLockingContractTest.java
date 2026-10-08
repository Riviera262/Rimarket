package RiMarket.com.example.demo.module.catalog.repository;

import RiMarket.com.example.demo.module.catalog.entity.Category;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.service.CategoryService;
import RiMarket.com.example.demo.module.catalog.service.ProductService;
import RiMarket.com.example.demo.module.catalog.dto.category.CreateCategoryRequest;
import RiMarket.com.example.demo.module.catalog.dto.category.UpdateCategoryRequest;
import RiMarket.com.example.demo.module.catalog.dto.product.CreateProductRequest;
import RiMarket.com.example.demo.module.catalog.dto.product.UpdateProductRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CatalogOptimisticLockingContractTest {

    @Test
    void structuralCategoryWritesUseSerializableIsolation() throws NoSuchMethodException {
        for (var method : java.util.List.of(
                CategoryService.class.getMethod("createCategory", CreateCategoryRequest.class),
                CategoryService.class.getMethod("updateCategory", UUID.class, UpdateCategoryRequest.class),
                CategoryService.class.getMethod("deleteCategory", UUID.class))) {
            assertEquals(Isolation.SERIALIZABLE, method.getAnnotation(Transactional.class).isolation());
        }
    }

    @Test
    void productCategoryAssignmentsUseSerializableIsolation() throws NoSuchMethodException {
        for (var method : java.util.List.of(
                ProductService.class.getMethod("createProduct", CreateProductRequest.class),
                ProductService.class.getMethod("updateProduct", UUID.class, UpdateProductRequest.class))) {
            assertEquals(Isolation.SERIALIZABLE, method.getAnnotation(Transactional.class).isolation());
        }
    }

    @Test
    void categoryAndProductAreVersioned() throws NoSuchFieldException {
        assertNotNull(Category.class.getDeclaredField("version").getAnnotation(Version.class));
        assertNotNull(Product.class.getDeclaredField("version").getAnnotation(Version.class));
    }

    @Test
    void childCommandQueryFetchesVariantsWithoutApplyingQueryLock() throws NoSuchMethodException {
        var method = ProductRepository.class
                .getMethod("findAggregateForChildCommandById", UUID.class);

        assertNull(method.getAnnotation(org.springframework.data.jpa.repository.Lock.class));
        assertNotNull(method.getAnnotation(EntityGraph.class),
                "Variants are fetched first; services then lock only the managed Product instance");
    }
}
