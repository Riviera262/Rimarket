package RiMarket.com.example.demo.module.inventory.controller;

import RiMarket.com.example.demo.module.inventory.domain.InventoryActor;
import RiMarket.com.example.demo.module.inventory.dto.*;
import RiMarket.com.example.demo.module.inventory.service.InventoryService;
import RiMarket.com.example.demo.shared.security.AuthenticatedUser;
import RiMarket.com.example.demo.shared.security.SecurityErrorCode;
import RiMarket.com.example.demo.shared.exception.AppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/management/inventory/items")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
@RequiredArgsConstructor
public class InventoryManagementController {
    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<Page<InventoryItemSummaryResponse>> list(
            @PageableDefault(size = 20, sort = "sku") Pageable pageable) {
        Page<InventoryItemSummaryResponse> response = inventoryService.getItems(pageable);
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/{variantId}")
    public ResponseEntity<InventoryItemDetailResponse> get(@PathVariable UUID variantId) {
        InventoryItemDetailResponse response = inventoryService.getItem(variantId);
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/{variantId}/receipts")
    public ResponseEntity<InventoryMovementResponse> receive(
            @PathVariable UUID variantId, @Valid @RequestBody ReceiveStockRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) throw new AppException(SecurityErrorCode.AUTHENTICATION_REQUIRED);
        InventoryActor actor = InventoryActor.user(user.userId(), user.email());
        InventoryMovementResponse response = inventoryService.receive(variantId, request, actor);
        return ResponseEntity.ok().body(response);
    }

    @PostMapping("/{variantId}/adjustments")
    public ResponseEntity<InventoryMovementResponse> adjust(
            @PathVariable UUID variantId, @Valid @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) throw new AppException(SecurityErrorCode.AUTHENTICATION_REQUIRED);
        InventoryActor actor = InventoryActor.user(user.userId(), user.email());
        InventoryMovementResponse response = inventoryService.adjust(variantId, request, actor);
        return ResponseEntity.ok().body(response);
    }

    @GetMapping("/{variantId}/movements")
    public ResponseEntity<Page<InventoryMovementResponse>> movements(
            @PathVariable UUID variantId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Page<InventoryMovementResponse> response = inventoryService.getMovements(variantId, page, size);
        return ResponseEntity.ok().body(response);
    }
}
