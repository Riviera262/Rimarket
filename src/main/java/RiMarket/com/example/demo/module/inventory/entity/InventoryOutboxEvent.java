package RiMarket.com.example.demo.module.inventory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryOutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;
    @Column(name = "event_version", nullable = false)
    private int eventVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;
    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;
    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    public static InventoryOutboxEvent stockChanged(InventoryMovement movement) {
        if (movement.getId() == null){
            throw new IllegalArgumentException("Persist movement before creating its event");
        }

        InventoryOutboxEvent event = new InventoryOutboxEvent();
        event.aggregateType = "InventoryItem";
        event.aggregateId = movement.getVariantId();
        event.eventType = "inventory.stock-changed";
        event.eventVersion = 1;
        event.occurredAt = movement.getCreatedAt();
        event.nextAttemptAt = event.occurredAt;
        event.payload = Map.of(
                "variantId", movement.getVariantId().toString(),
                "movementId", movement.getId().toString(),
                "operationId", movement.getOperationId().toString(),
                "movementType", movement.getMovementType().name(),
                "onHandBefore", movement.getOnHandBefore(),
                "onHandAfter", movement.getOnHandAfter(),
                "onHandDelta", movement.getOnHandDelta(),
                "reservedBefore", movement.getReservedBefore(),
                "reservedAfter", movement.getReservedAfter(),
                "reservedDelta", movement.getReservedDelta());
        return event;
    }
}
