package RiMarket.com.example.demo.module.catalog.controller.storefront;

import RiMarket.com.example.demo.module.catalog.dto.product.ProductDetailResponse;
import RiMarket.com.example.demo.module.catalog.dto.product.ProductSummaryResponse;
import RiMarket.com.example.demo.module.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    //=======================================================
    // GET
    //=======================================================

    //Get all products from a category OR get all products in DB(Status: active)
    @GetMapping
    public ResponseEntity<Page<ProductSummaryResponse>> getProducts(@RequestParam(required = false) UUID categoryId, Pageable pageable){
        Page<ProductSummaryResponse> response;

        if (categoryId != null){
            response = productService.getProductsByCategory(categoryId, pageable);
        } else {
            response = productService.getProducts(pageable);
        }

        return ResponseEntity.ok().body(response);
    }

    //Get product by its own slug and active = true
    @GetMapping("/{slug}")
    public ResponseEntity<ProductDetailResponse> getProductDetail(@PathVariable String slug) {
        ProductDetailResponse response = productService.getProductDetailBySlug(slug);

        return ResponseEntity.ok().body(response);
    }

}
