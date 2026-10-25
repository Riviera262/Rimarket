package RiMarket.com.example.demo.module.inventory.entity;

import RiMarket.com.example.demo.module.inventory.domain.*;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainException;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainViolation;
import RiMarket.com.example.demo.module.inventory.domain.policy.InventoryItemPolicy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "items", schema = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryItem {
    @Id
    @Column(name = "variant_id")
    private UUID variantId;

    @Column(nullable = false, length = 100)
    private String sku;
    @Column(name = "product_name", nullable = false)
    private String productName;
    @Column(name = "variant_name", nullable = false)
    private String variantName;

    @Column(name = "on_hand_qty", nullable = false)
    private int onHandQty;
    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;

    // Nullable wrapper lets Spring Data recognize new entities with assigned IDs.
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public InventoryItem(UUID variantId, String sku, String productName, String variantName) {
        if (variantId == null) {
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_METADATA);
        }
        this.variantId = variantId;
        this.sku = InventoryItemPolicy.normalizeSku(sku);
        this.productName = InventoryItemPolicy.normalizeName(productName);
        this.variantName = InventoryItemPolicy.normalizeName(variantName);
    }

    public int getAvailableQty() {

        return onHandQty - reservedQty;
    }

    public InventoryMovement receive(int quantity, MovementContext context) {
        if (quantity <= 0) {
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_QUANTITY);
        }

        return changePhysicalStock(MovementType.RECEIVE, quantity, context);
    }

    public InventoryMovement adjust(int delta, MovementContext context) {
        if (delta == 0) {
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_QUANTITY);
        }

        if (context == null || context.reason() == null) {
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_METADATA);
        }

        return changePhysicalStock(MovementType.ADJUSTMENT, delta, context);
    }

    private InventoryMovement changePhysicalStock(MovementType type, int delta, MovementContext context) {
        if (context == null) {
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_OPERATION);
        }

        long after = (long) onHandQty + delta;

        //Make sure the available quantity do not lower than reserve quantity that we graduate for customer
        if (after < reservedQty || after > Integer.MAX_VALUE) {
            throw new InventoryDomainException(InventoryDomainViolation.STOCK_OUT_OF_RANGE);
        }

        InventoryMovement movement = new InventoryMovement(variantId, type, onHandQty, (int) after, reservedQty, reservedQty, context);

        onHandQty = (int) after;

        return movement;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
