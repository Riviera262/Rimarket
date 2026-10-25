package RiMarket.com.example.demo.application.variant;

import RiMarket.com.example.demo.module.catalog.api.CatalogVariantCommands;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.CreateProductVariantRequest;
import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.inventory.api.InventoryItemCommands;
import RiMarket.com.example.demo.module.inventory.domain.InventoryActor;
import RiMarket.com.example.demo.module.inventory.dto.InventoryItemResponse;
import RiMarket.com.example.demo.shared.exception.AppException;
import RiMarket.com.example.demo.shared.security.SecurityErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.UUID;

/** Orchestrator: coordinates public module APIs, never manipulates their entities. */
@Service
@Validated
@RequiredArgsConstructor
public class CreateVariantWithStockService {
    private final CatalogVariantCommands catalogVariantCommands;
    private final InventoryItemCommands inventoryItemCommands;
    private final VariantCreationRequestStore requestStore;

    // Both module commands use REQUIRED and join this transaction.
    // Success means the request receipt, variant, item, optional movement and outbox all committed.
    // Inventory is created synchronously; no Catalog event is needed for this workflow.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateVariantWithStockResponse create(
            @NotNull UUID productId,
            @NotNull @Valid CreateVariantWithStockRequest request,
            @NotNull InventoryActor actor) {

        // This management workflow requires a user-scoped key, not an anonymous/system actor.
        if (actor.id() == null){
            throw new AppException(SecurityErrorCode.AUTHENTICATION_REQUIRED);
        }

        String hash = VariantCreationFingerprint.of(productId, request);

        Optional<CreateVariantWithStockResponse> replay = requestStore.claimOrReplay(
                actor.id(), request.operationId(), hash);

        if (replay.isPresent()){
            return replay.get();
        }

        CreateProductVariantRequest variantRequest = new CreateProductVariantRequest(request.name(), request.price());

        ProductVariantResponse variant = catalogVariantCommands.addVariant(productId, variantRequest);
        InventoryItemResponse inventory = inventoryItemCommands.createItemWithStock(variant.id(), request.initialQuantity(), request.operationId(), actor);

        CreateVariantWithStockResponse response = new CreateVariantWithStockResponse(variant, inventory);
        requestStore.complete(actor.id(), request.operationId(), response);
        return response;
    }
}
