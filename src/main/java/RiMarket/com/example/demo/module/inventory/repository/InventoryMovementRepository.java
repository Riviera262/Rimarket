package RiMarket.com.example.demo.module.inventory.repository;

import RiMarket.com.example.demo.module.inventory.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.UUID;

// Deliberately no update/delete API for audit records.
public interface InventoryMovementRepository extends Repository<InventoryMovement, UUID> {

    InventoryMovement save(InventoryMovement movement);

    Optional<InventoryMovement> findByVariantIdAndOperationId(UUID variantId, UUID operationId);

    Page<InventoryMovement> findByVariantId(UUID variantId, Pageable pageable);
}
