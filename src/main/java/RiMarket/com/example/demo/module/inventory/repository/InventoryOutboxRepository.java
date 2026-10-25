package RiMarket.com.example.demo.module.inventory.repository;

import RiMarket.com.example.demo.module.inventory.entity.InventoryOutboxEvent;
import org.springframework.data.repository.Repository;
import java.util.UUID;

public interface InventoryOutboxRepository extends Repository<InventoryOutboxEvent, UUID> {

    InventoryOutboxEvent save(InventoryOutboxEvent event);
}
