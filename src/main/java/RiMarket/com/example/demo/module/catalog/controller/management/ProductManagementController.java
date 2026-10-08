package RiMarket.com.example.demo.module.catalog.controller.management;

import RiMarket.com.example.demo.module.catalog.dto.product.CreateProductRequest;
import RiMarket.com.example.demo.module.catalog.dto.product.ProductSummaryResponse;
import RiMarket.com.example.demo.module.catalog.dto.product.UpdateProductRequest;
import RiMarket.com.example.demo.module.catalog.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/management/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class ProductManagementController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductSummaryResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        ProductSummaryResponse response = productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ProductSummaryResponse> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {

        ProductSummaryResponse response = productService.updateProduct(productId, request);

        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/{productId}/activate")
    public ResponseEntity<Void> activateProduct(@PathVariable UUID productId) {
        productService.activateProduct(productId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/deactivate")
    public ResponseEntity<Void> deActivateProduct(@PathVariable UUID productId) {
        productService.deactivateProduct(productId);

        return ResponseEntity.noContent().build();
    }

    //HARD DELETE(DANGEROUS!!!)
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID productId){
        productService.deleteDraftProduct(productId);

        return ResponseEntity.noContent().build();
    }
}
