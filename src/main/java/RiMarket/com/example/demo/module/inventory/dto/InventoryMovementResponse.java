package RiMarket.com.example.demo.module.inventory.dto;

import RiMarket.com.example.demo.module.inventory.domain.ActorType;
import RiMarket.com.example.demo.module.inventory.domain.MovementType;
import RiMarket.com.example.demo.module.inventory.entity.InventoryMovement;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

public record InventoryMovementResponse(
        UUID id,
        UUID variantId,
        UUID operationId,
        MovementType movementType,
        int onHandBefore,
        int onHandAfter,
        int onHandDelta,
        int reservedBefore,
        int reservedAfter,
        int reservedDelta,
        ActorType actorType,
        UUID actorId,
        String actorLabel,
        String referenceType,
        UUID referenceId,
        String reason,
        ZonedDateTime createdAt
) {

}
