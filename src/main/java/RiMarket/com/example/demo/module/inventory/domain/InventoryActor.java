package RiMarket.com.example.demo.module.inventory.domain;

import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainException;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainViolation;
import RiMarket.com.example.demo.module.inventory.domain.support.InventoryText;

import java.util.UUID;

public record InventoryActor(ActorType type, UUID id, String label) {
    public InventoryActor {
        if (type == null || (type == ActorType.USER && id == null) || (type == ActorType.SYSTEM && id != null)) {
            throw new InventoryDomainException(InventoryDomainViolation.INVALID_ACTOR);
        }
        label = InventoryText.requireText(label, 255, InventoryDomainViolation.INVALID_ACTOR);
    }

    public static InventoryActor user(UUID id, String label) {
        return new InventoryActor(ActorType.USER, id, label);
    }
}
