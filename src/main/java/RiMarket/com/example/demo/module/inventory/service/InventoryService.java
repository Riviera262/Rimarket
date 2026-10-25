package RiMarket.com.example.demo.module.inventory.service;

import RiMarket.com.example.demo.module.catalog.api.CatalogVariantLookup;
import RiMarket.com.example.demo.module.catalog.api.CatalogVariantSnapshot;
import RiMarket.com.example.demo.module.inventory.api.InventoryItemCommands;
import RiMarket.com.example.demo.module.inventory.domain.*;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainException;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainViolation;
import RiMarket.com.example.demo.module.inventory.dto.*;
import RiMarket.com.example.demo.module.inventory.entity.*;
import RiMarket.com.example.demo.module.inventory.exception.InventoryErrorCode;
import RiMarket.com.example.demo.module.inventory.mapper.InventoryItemMapper;
import RiMarket.com.example.demo.module.inventory.mapper.InventoryMovementMapper;
import RiMarket.com.example.demo.module.inventory.repository.*;
import RiMarket.com.example.demo.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService implements InventoryItemCommands {
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final CatalogVariantLookup catalogVariantLookup;

    @Transactional
    public InventoryItemResponse createItem(UUID variantId) {
        InventoryItem item = initializeItem(variantId);
        return inventoryItemMapper.toResponse(item);
    }

    @Override
    @Transactional
    public InventoryItemResponse createItemWithStock(UUID variantId, int initialQuantity, UUID operationId, InventoryActor actor) {
        if (initialQuantity < 0) {
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_QUANTITY);
        }
        MovementContext context = new MovementContext(operationId, actor, null, null, "Initial stock at variant creation");

        // Reuse zero-stock initialization; creation is exposed only through the orchestrator.
        InventoryItem item = initializeItem(variantId);
        if (initialQuantity > 0) {
            changeStock(variantId, MovementType.RECEIVE, initialQuantity, context);
        }

        // changeStock flushes the updated quantity/version before mapping the response.
        return inventoryItemMapper.toResponse(item);
    }

    private InventoryItem initializeItem(UUID variantId) {
        if (variantId == null){
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_METADATA);
        }

        if (inventoryItemRepository.existsById(variantId)) {
            throw new AppException(InventoryErrorCode.ITEM_ALREADY_EXISTS);
        }

        CatalogVariantSnapshot variant = catalogVariantLookup.findVariant(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.CATALOG_VARIANT_NOT_FOUND));

        InventoryItem item = new InventoryItem(variant.variantId(), variant.sku(), variant.productName(), variant.variantName());

        inventoryItemRepository.saveAndFlush(item);

        // Zero stock initialization has no quantity movement.
        return item;
    }

    public InventoryItemDetailResponse getItem(UUID variantId) {
        InventoryItem item = loadItem(variantId);

        // Synchronous Java call to Catalog's public API, not HTTP or an event.
        var images = catalogVariantLookup.findImages(List.of(variantId));
        return inventoryItemMapper.toDetailResponse(item, images);
    }

    public Page<InventoryItemSummaryResponse> getItems(Pageable pageable) {
        pageable = itemPageRequest(pageable);
        Page<InventoryItem> items = inventoryItemRepository.findAll(pageable);
        Map<UUID, String> thumbnails = new HashMap<>();

        if (items.hasContent()) {
            List<UUID> variantIds = items.getContent()
                    .stream()
                    .map(InventoryItem::getVariantId)
                    .toList();

            // One image query for this page. The first image has the smallest sortOrder,
            // even if there is no image with sortOrder = 0.
            for (var image : catalogVariantLookup.findImages(variantIds)) {
                thumbnails.putIfAbsent(image.variantId(), image.url());
            }
        }
        return items.map(item -> inventoryItemMapper.toSummaryResponse(item, thumbnails.get(item.getVariantId())));
    }

    public Page<InventoryMovementResponse> getMovements(UUID variantId, int page, int size) {
        loadItem(variantId);
        return inventoryMovementRepository.findByVariantId(variantId,
                pageRequest(page, size, "createdAt", "id").withSort(Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(inventoryMovementMapper::toResponse);
    }

    @Transactional
    public InventoryMovementResponse receive(UUID variantId, ReceiveStockRequest request, InventoryActor actor) {
        MovementContext context = new MovementContext(request.operationId(), actor,
                request.referenceType(), request.referenceId(), request.reason());
        return changeStock(variantId, MovementType.RECEIVE, request.quantity(), context);
    }

    @Transactional
    public InventoryMovementResponse adjust(UUID variantId, AdjustStockRequest request, InventoryActor actor) {
        MovementContext context = new MovementContext(request.operationId(), actor,
                request.referenceType(), request.referenceId(), request.reason());
        return changeStock(variantId, MovementType.ADJUSTMENT, request.delta(), context);
    }

    private InventoryMovementResponse changeStock(UUID variantId, MovementType type, int delta, MovementContext context) {
        var previous = inventoryMovementRepository.findByVariantIdAndOperationId(variantId, context.operationId());
        if (previous.isPresent()) {
            if (!previous.get().matches(type, delta, context)) {
                throw new AppException(InventoryErrorCode.OPERATION_CONFLICT);
            }
            // Return the original result, even if the item's stock has since changed.
            return inventoryMovementMapper.toResponse(previous.get());
        }

        InventoryItem item = loadItem(variantId);
        InventoryMovement movement = switch (type) {
            case RECEIVE -> item.receive(delta, context);
            case ADJUSTMENT -> item.adjust(delta, context);
            default -> throw new IllegalArgumentException("Reservation commands are not implemented yet");
        };
        inventoryMovementRepository.save(movement);
        inventoryOutboxRepository.save(InventoryOutboxEvent.stockChanged(movement));
        // Dirty checking + @Version protects item changes. Any conflict rolls back all three writes.
        inventoryItemRepository.flush();
        return inventoryMovementMapper.toResponse(movement);
    }

    private InventoryItem loadItem(UUID variantId) {

        return inventoryItemRepository.findById(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.ITEM_NOT_FOUND));
    }

    private Pageable itemPageRequest(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() > 100) {
            throw new AppException(InventoryErrorCode.INVALID_PAGE);
        }
        Set<String> allowedSorts = Set.of("variantId", "sku", "productName", "variantName",
                "onHandQty", "reservedQty", "createdAt", "updatedAt");
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by("sku");
        for (Sort.Order order : sort) {
            if (!allowedSorts.contains(order.getProperty())) {
                throw new AppException(InventoryErrorCode.INVALID_PAGE);
            }
        }
        if (sort.getOrderFor("variantId") == null) sort = sort.and(Sort.by("variantId"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private PageRequest pageRequest(int page, int size, String... sortProperties) {
        if (page < 0 || size < 1 || size > 100) throw new AppException(InventoryErrorCode.INVALID_PAGE);
        return PageRequest.of(page, size, Sort.by(sortProperties));
    }
}
