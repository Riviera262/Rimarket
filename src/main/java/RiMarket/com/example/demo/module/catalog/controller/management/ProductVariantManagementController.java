package RiMarket.com.example.demo.module.catalog.controller.management;

import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ReorderProductVariantsRequest;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.UpdateProductVariantRequest;
import RiMarket.com.example.demo.module.catalog.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/management/products/{productId}/variants")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class ProductVariantManagementController {

    private final ProductVariantService productVariantService;

    // Creation is exposed only by application.variant.VariantStockManagementController.
    //Update 1 variant in a product
    @PutMapping("/{variantId}")
    public ResponseEntity<ProductVariantResponse> updateVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateProductVariantRequest request) {

        ProductVariantResponse response = productVariantService.updateVariant(productId, variantId, request);

        return ResponseEntity.ok().body(response);
    }

    //Variant switch status to activate
    @PostMapping("/{variantId}/activate")
    public ResponseEntity<Void> activateVariant(@PathVariable UUID productId, @PathVariable UUID variantId) {
        productVariantService.activateVariant(productId, variantId);

        return ResponseEntity.noContent().build();
    }

    //Variant switch status to deactivate
    @PostMapping("/{variantId}/deactivate")
    public ResponseEntity<Void> deactivateVariant(@PathVariable UUID productId, @PathVariable UUID variantId) {
        productVariantService.deactivateVariant(productId, variantId);

        return ResponseEntity.noContent().build();
    }

    //Reorder every variant order in a product
    @PutMapping("/order")
    public ResponseEntity<Void> reorderVariants(@PathVariable UUID productId, @Valid @RequestBody ReorderProductVariantsRequest request){
        productVariantService.reorder(productId, request);

        return ResponseEntity.noContent().build();
    }
}
