package RiMarket.com.example.demo.module.inventory.mapper;

import RiMarket.com.example.demo.module.inventory.dto.InventoryMovementResponse;
import RiMarket.com.example.demo.module.inventory.entity.InventoryMovement;
import org.springframework.stereotype.Component;

@Component
public class InventoryMovementMapper {

    public InventoryMovementResponse toResponse(InventoryMovement movement){
        if (movement == null){
            return null;
        }
        return new InventoryMovementResponse(
                movement.getId(),
                movement.getVariantId(),
                movement.getOperationId(),
                movement.getMovementType(),
                movement.getOnHandBefore(),
                movement.getOnHandAfter(),
                movement.getOnHandDelta(),
                movement.getReservedBefore(),
                movement.getReservedAfter(),
                movement.getReservedDelta(),
                movement.getActorType(),
                movement.getActorId(),
                movement.getActorLabel(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getReason(),
                movement.getCreatedAt());
    }
}
