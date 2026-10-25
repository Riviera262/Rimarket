package RiMarket.com.example.demo.module.inventory.entity;

import RiMarket.com.example.demo.module.inventory.domain.*;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryItemTest {
    private InventoryItem item() {
        return new InventoryItem(UUID.randomUUID(), "SKU", "Product", "Variant");
    }

    private MovementContext context(String reason) {
        return new MovementContext(UUID.randomUUID(),
                InventoryActor.user(UUID.randomUUID(), "Test staff"), null, null, reason);
    }

    @Test
    void receiptAndAdjustmentCaptureActualBeforeAndAfter() {
        var item = item();
        var receipt = item.receive(10, context(null));
        assertEquals(0, receipt.getOnHandBefore());
        assertEquals(10, receipt.getOnHandAfter());
        var adjustment = item.adjust(-3, context("Damaged"));
        assertEquals(10, adjustment.getOnHandBefore());
        assertEquals(7, adjustment.getOnHandAfter());
        assertEquals(-3, adjustment.getOnHandDelta());
        assertEquals(0, adjustment.getReservedDelta());
        assertEquals(7, item.getAvailableQty());
    }

    @Test
    void rejectsZeroNegativeReceiptAndZeroAdjustmentWithoutMutation() {
        var item = item();
        assertThrows(InventoryDomainException.class, () -> item.receive(0, context(null)));
        assertThrows(InventoryDomainException.class, () -> item.receive(-1, context(null)));
        assertThrows(InventoryDomainException.class, () -> item.adjust(0, context("Count")));
        assertEquals(0, item.getOnHandQty());
    }

    @Test
    void rejectsOverflowAndNegativeStockWithoutMutation() {
        var item = item();
        item.receive(Integer.MAX_VALUE, context(null));
        assertThrows(InventoryDomainException.class, () -> item.receive(1, context(null)));
        assertThrows(InventoryDomainException.class, () -> item.adjust(Integer.MIN_VALUE, context("Count")));
        assertEquals(Integer.MAX_VALUE, item.getOnHandQty());
    }

    @Test
    void physicalCorrectionCannotConsumeReservedStock() {
        var item = item();
        item.receive(10, context(null));
        ReflectionTestUtils.setField(item, "reservedQty", 8);
        assertThrows(InventoryDomainException.class, () -> item.adjust(-3, context("Count")));
        assertEquals(10, item.getOnHandQty());
        assertEquals(8, item.getReservedQty());
    }

    @Test
    void requiresAdjustmentReasonAndCompleteAuditContext() {
        var item = item();
        assertThrows(InventoryDomainException.class, () -> item.adjust(1, context(null)));
        assertThrows(InventoryDomainException.class, () -> item.receive(1, null));
        assertThrows(InventoryDomainException.class, () -> new InventoryActor(ActorType.USER, null, "Staff"));
        assertThrows(InventoryDomainException.class, () -> new InventoryActor(ActorType.SYSTEM, UUID.randomUUID(), "Job"));
        assertThrows(InventoryDomainException.class, () -> new MovementContext(
                UUID.randomUUID(), context(null).actor(), "ORDER", null, null));
        assertEquals(0, item.getOnHandQty());
    }
}
