package RiMarket.com.example.demo.module.catalog.controller.storefront;

import RiMarket.com.example.demo.module.catalog.dto.category.CategoryResponse;
import RiMarket.com.example.demo.module.catalog.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;


    //=======================================================
    // GET
    //=======================================================

    //Get a single category
    @GetMapping("/{categoryId}")
    public ResponseEntity<CategoryResponse> getCategoryById(@PathVariable UUID categoryId){
        CategoryResponse response = categoryService.getCategoryById(categoryId);

        return ResponseEntity.ok().body(response);
    }

    //Get tree category
    @GetMapping("/tree")
    public ResponseEntity<List<CategoryResponse>> getCategoryTree(){
        List<CategoryResponse> response = categoryService.getCategoryTree();

        return ResponseEntity.ok().body(response);
    }

    //Get category children
    @GetMapping("/{categoryId}/children")
    public ResponseEntity<List<CategoryResponse>> getCategoryChildren(@PathVariable UUID categoryId){
        List<CategoryResponse> response = categoryService.getChildren(categoryId);

        return ResponseEntity.ok().body(response);
    }

}
