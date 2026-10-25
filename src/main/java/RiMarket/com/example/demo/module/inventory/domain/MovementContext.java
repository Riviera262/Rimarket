package RiMarket.com.example.demo.module.inventory.domain;

import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainException;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainViolation;
import RiMarket.com.example.demo.module.inventory.domain.support.InventoryText;

import java.util.UUID;

public record MovementContext(UUID operationId, InventoryActor actor, String referenceType, UUID referenceId, String reason) {

    public MovementContext {
        if (operationId == null){
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_OPERATION);
        }

        if (actor == null){
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_ACTOR);
        }

        referenceType = InventoryText.optionalText(referenceType, 50, InventoryDomainViolation.INVALID_REFERENCE);

        if ((referenceType == null) != (referenceId == null)) {
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_REFERENCE);
        }

        reason = InventoryText.optionalText(reason, 1000, InventoryDomainViolation.INVALID_METADATA);
    }
}
