package RiMarket.com.example.demo.module.catalog.controller.management;

import RiMarket.com.example.demo.module.catalog.dto.category.CategoryResponse;
import RiMarket.com.example.demo.module.catalog.dto.category.CreateCategoryRequest;
import RiMarket.com.example.demo.module.catalog.dto.category.UpdateCategoryRequest;
import RiMarket.com.example.demo.module.catalog.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/management/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN')")
public class CategoryManagementController {

    private final CategoryService categoryService;

    //Create 1 category
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request){
        CategoryResponse response = categoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    //Update 1 category
    @PutMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request){
        CategoryResponse response = categoryService.updateCategory(categoryId,request);

        return ResponseEntity.ok().body(response);
    }

    //Delete 1 category and return nothing
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID categoryId){
        categoryService.deleteCategory(categoryId);

        return ResponseEntity.noContent().build();
    }
}
