package RiMarket.com.example.demo.module.inventory.repository;

import RiMarket.com.example.demo.module.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {}
