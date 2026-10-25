package RiMarket.com.example.demo.application.variant;

import RiMarket.com.example.demo.module.inventory.domain.InventoryActor;
import RiMarket.com.example.demo.shared.security.AuthenticatedUser;
import RiMarket.com.example.demo.shared.security.SecurityErrorCode;
import RiMarket.com.example.demo.shared.exception.AppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/management/products/{productId}/variants")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class VariantStockManagementController {
    private final CreateVariantWithStockService service;

    @PostMapping
    public ResponseEntity<CreateVariantWithStockResponse> create(
            @PathVariable UUID productId,
            @Valid @RequestBody CreateVariantWithStockRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        if (user == null) {
            throw new AppException(SecurityErrorCode.AUTHENTICATION_REQUIRED);
        }

        InventoryActor actor = InventoryActor.user(user.userId(), user.email());

        CreateVariantWithStockResponse response = service.create(productId, request, actor);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
