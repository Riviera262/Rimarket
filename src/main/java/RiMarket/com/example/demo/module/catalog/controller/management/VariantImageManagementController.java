package RiMarket.com.example.demo.module.catalog.controller.management;

import RiMarket.com.example.demo.module.catalog.dto.imageVariant.ReorderVariantImagesRequest;
import RiMarket.com.example.demo.module.catalog.dto.imageVariant.VariantImageResponse;
import RiMarket.com.example.demo.module.catalog.service.VariantImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/management/products/{productId}/variants/{variantId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public class VariantImageManagementController {

    private final VariantImageService variantImageService;

    @GetMapping
    public ResponseEntity<List<VariantImageResponse>> getImages(
            @PathVariable UUID productId,
            @PathVariable UUID variantId
    ) {
        List<VariantImageResponse> response = variantImageService.getImages(productId, variantId);

        return ResponseEntity.ok().body(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<VariantImageResponse>> uploadImages(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @RequestPart("files") List<MultipartFile> files
    ) {
        List<VariantImageResponse> response = variantImageService.uploadImages(productId, variantId, files);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PutMapping(value = "/{imageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VariantImageResponse> replaceImage(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @PathVariable UUID imageId,
            @RequestPart("file") MultipartFile file
    ) {
        VariantImageResponse response = variantImageService.replaceImage(
                productId,
                variantId,
                imageId,
                file
        );

        return ResponseEntity.ok().body(response);
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @PathVariable UUID imageId
    ) {
        variantImageService.deleteImage(productId, variantId, imageId);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    public ResponseEntity<Void> reorderImages(
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody ReorderVariantImagesRequest request
    ) {
        variantImageService.reorderImages(productId, variantId, request.imageIds());

        return ResponseEntity.noContent().build();
    }
}
