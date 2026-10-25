package RiMarket.com.example.demo.module.inventory.entity;

import RiMarket.com.example.demo.module.inventory.domain.*;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "movements",
        schema = "inventory",
        uniqueConstraints = @UniqueConstraint(name = "uq_inventory_movement_operation",
        columnNames = {"variant_id", "operation_id"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;
    @Column(name = "operation_id", nullable = false, updatable = false)
    private UUID operationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;
    @Column(name = "on_hand_before", nullable = false)
    private int onHandBefore;
    @Column(name = "on_hand_after", nullable = false)
    private int onHandAfter;
    @Column(name = "on_hand_delta", nullable = false)
    private int onHandDelta;
    @Column(name = "reserved_before", nullable = false)
    private int reservedBefore;
    @Column(name = "reserved_after", nullable = false)
    private int reservedAfter;
    @Column(name = "reserved_delta", nullable = false)
    private int reservedDelta;
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 10)
    private ActorType actorType;
    @Column(name = "actor_id")
    private UUID actorId;
    @Column(name = "actor_label", nullable = false)
    private String actorLabel;
    @Column(name = "reference_type", length = 50)
    private String referenceType;
    @Column(name = "reference_id")
    private UUID referenceId;
    @Column(length = 1000)
    private String reason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    // Only the InventoryItem aggregate creates movements from a validated state transition.
    InventoryMovement(UUID variantId, MovementType type, int before, int after, int reservedBefore, int reservedAfter, MovementContext context) {
        this.variantId = variantId;
        movementType = type;
        onHandBefore = before;
        onHandAfter = after;
        onHandDelta = after - before;
        this.reservedBefore = reservedBefore;
        this.reservedAfter = reservedAfter;
        reservedDelta = reservedAfter - reservedBefore;
        operationId = context.operationId();
        actorType = context.actor().type();
        actorId = context.actor().id();
        actorLabel = context.actor().label();
        referenceType = context.referenceType();
        referenceId = context.referenceId();
        reason = context.reason();
    }

    public boolean matches(MovementType type, int delta, MovementContext context) {
        return movementType == type && onHandDelta == delta
                && actorType == context.actor().type() && Objects.equals(actorId, context.actor().id())
                && Objects.equals(referenceType, context.referenceType())
                && Objects.equals(referenceId, context.referenceId())
                && Objects.equals(reason, context.reason());
    }

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
}
