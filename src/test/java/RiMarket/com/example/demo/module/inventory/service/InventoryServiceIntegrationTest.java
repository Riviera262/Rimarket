package RiMarket.com.example.demo.module.inventory.service;

import RiMarket.com.example.demo.application.variant.CreateVariantWithStockService;
import RiMarket.com.example.demo.application.variant.CreateVariantWithStockRequest;
import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainException;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.repository.CategoryRepository;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import RiMarket.com.example.demo.module.inventory.domain.*;
import RiMarket.com.example.demo.module.inventory.dto.*;
import RiMarket.com.example.demo.module.inventory.entity.*;
import RiMarket.com.example.demo.module.inventory.exception.InventoryErrorCode;
import RiMarket.com.example.demo.shared.exception.AppException;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryServiceIntegrationTest {
    @Autowired InventoryService service;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbc;
    @Autowired CreateVariantWithStockService createVariantWithStockService;

    UUID rootId, categoryId, productId, variantId;
    final InventoryActor actor = InventoryActor.user(UUID.randomUUID(), "Test staff");

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            var root = categories.save(new Category("Inventory root " + suffix, null));
            var category = categories.save(new Category("Inventory child " + suffix, root));
            var product = new Product("Inventory product", "inventory-" + suffix, null, category);
            var variant = product.addVariant("INV-" + suffix, "Black", BigDecimal.TEN);
            products.saveAndFlush(product);
            rootId = root.getId();
            categoryId = category.getId();
            productId = product.getId();
            variantId = variant.getId();
        });
    }

    @AfterEach
    void cleanOnlyThisTestsGeneratedFixtures() {
        if (variantId == null) return;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // Only this fixture's product, including variants created by orchestration tests.
            jdbc.update("delete from application.variant_creation_requests where actor_id = ?", actor.id());
            jdbc.update("delete from inventory.outbox_events where aggregate_id in " +
                    "(select id from catalog.product_variants where product_id = ?)", productId);
            jdbc.update("delete from inventory.movements where variant_id in " +
                    "(select id from catalog.product_variants where product_id = ?)", productId);
            jdbc.update("delete from inventory.items where variant_id in " +
                    "(select id from catalog.product_variants where product_id = ?)", productId);
            jdbc.update("delete from catalog.outbox_events where aggregate_id = ?", productId);
            products.deleteById(productId);
            products.flush();
            categories.deleteById(categoryId);
            categories.flush();
            categories.deleteById(rootId);
        });
    }

    @Test
    void initializesFromCatalogWithAssignedVariantIdAndRejectsUnknownVariant() {
        var item = service.createItem(variantId);
        assertEquals(variantId, item.variantId());
        assertEquals("Inventory product", item.productName());
        assertEquals("Black", item.variantName());
        assertEquals(0, item.availableQty());
        assertEquals(0L, item.version());
        assertEquals(0, count("inventory.movements", "variant_id"));
        assertEquals(InventoryErrorCode.ITEM_ALREADY_EXISTS,
                assertThrows(AppException.class, () -> service.createItem(variantId)).getErrorCode());
        assertEquals(InventoryErrorCode.CATALOG_VARIANT_NOT_FOUND,
                assertThrows(AppException.class, () -> service.createItem(UUID.randomUUID())).getErrorCode());
    }

    @Test
    void receiptPersistsStockAuditAndJsonOutboxAndRetryReturnsOriginalResult() {
        service.createItem(variantId);
        var request = receipt(UUID.randomUUID(), 10);
        var first = service.receive(variantId, request, actor);
        service.adjust(variantId, new AdjustStockRequest(UUID.randomUUID(), -2, "Damaged", null, null), actor);
        var replay = service.receive(variantId, request, actor);
        assertEquals(first.id(), replay.id());
        assertEquals(10, replay.onHandAfter());
        assertEquals(8, service.getItem(variantId).onHandQty());
        assertEquals(2, count("inventory.movements", "variant_id"));
        assertEquals(2, count("inventory.outbox_events", "aggregate_id"));
        assertEquals(7, first.id().version());
        UUID eventId = jdbc.queryForObject(
                "select id from inventory.outbox_events where payload->>'movementId' = ?", UUID.class, first.id().toString());
        assertNotNull(eventId);
        assertEquals(7, eventId.version());
        assertEquals("10", jdbc.queryForObject(
                "select payload->>'onHandAfter' from inventory.outbox_events where id = ?", String.class, eventId));
        assertEquals(actor.id(), first.actorId());
        assertEquals(2, service.getMovements(variantId, 0, 20).getTotalElements());
    }

    @Test
    void sameOperationWithDifferentPayloadOrActorIsRejected() {
        service.createItem(variantId);
        UUID operation = UUID.randomUUID();
        service.receive(variantId, receipt(operation, 10), actor);
        assertEquals(InventoryErrorCode.OPERATION_CONFLICT,
                assertThrows(AppException.class, () -> service.receive(variantId, receipt(operation, 11), actor)).getErrorCode());
        assertEquals(InventoryErrorCode.OPERATION_CONFLICT,
                assertThrows(AppException.class, () -> service.receive(variantId, receipt(operation, 10),
                        InventoryActor.user(UUID.randomUUID(), "Other staff"))).getErrorCode());
        assertEquals(10, service.getItem(variantId).onHandQty());
        assertEquals(1, count("inventory.outbox_events", "aggregate_id"));
    }

    @Test
    void rollbackAfterFlushingAlsoRollsBackStockMovementAndOutbox() {
        service.createItem(variantId);
        assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    service.receive(variantId, receipt(UUID.randomUUID(), 10), actor);
                    throw new IllegalStateException("Simulated failure before transaction commit");
                }));
        assertEquals(0, service.getItem(variantId).onHandQty());
        assertEquals(0, count("inventory.movements", "variant_id"));
        assertEquals(0, count("inventory.outbox_events", "aggregate_id"));
    }

    @Test
    void overlappingStaleWriterCannotCommitStockOrExtraEvents() {
        service.createItem(variantId);
        var stale = entityManagerFactory.createEntityManager();
        try {
            stale.getTransaction().begin();
            var item = stale.find(InventoryItem.class, variantId);
            // A second transaction commits while the first still holds a stale version.
            service.receive(variantId, receipt(UUID.randomUUID(), 10), actor);
            var movement = item.receive(7, new MovementContext(UUID.randomUUID(), actor, null, null, null));
            stale.persist(movement);
            stale.persist(InventoryOutboxEvent.stockChanged(movement));
            assertThrows(OptimisticLockException.class, stale::flush);
        } finally {
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            stale.close();
        }
        assertEquals(10, service.getItem(variantId).onHandQty());
        assertEquals(1, count("inventory.movements", "variant_id"));
        assertEquals(1, count("inventory.outbox_events", "aggregate_id"));
    }

    private ReceiveStockRequest receipt(UUID operation, int quantity) {
        return new ReceiveStockRequest(operation, quantity, null, null, null);
    }

    @Test
    void combinedCreateCommitsVariantStockAuditAndOnlyInventoryOutbox() {
        var response = createVariantWithStockService.create(productId,
                new CreateVariantWithStockRequest("White", BigDecimal.TEN, 10, UUID.randomUUID()), actor);
        UUID createdId = response.variant().id();
        assertEquals(createdId, response.inventory().variantId());
        assertEquals(10, response.inventory().onHandQty());
        assertEquals(0, response.inventory().reservedQty());
        assertEquals(10, response.inventory().availableQty());
        assertEquals(10, service.getItem(createdId).onHandQty());
        var movement = service.getMovements(createdId, 0, 20).getContent().getFirst();
        assertEquals(MovementType.RECEIVE, movement.movementType());
        assertEquals(actor.id(), movement.actorId());
        assertEquals(0, movement.onHandBefore());
        assertEquals(10, movement.onHandAfter());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from catalog.outbox_events where aggregate_id = ? and payload->>'variantId' = ?",
                Integer.class, productId, createdId.toString()));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from inventory.outbox_events where aggregate_id = ?", Integer.class, createdId));
    }

    @Test
    void combinedZeroStockCreatesItemWithoutMovementOrStockChangedEvent() {
        var response = createVariantWithStockService.create(productId,
                new CreateVariantWithStockRequest("White", BigDecimal.TEN, 0, UUID.randomUUID()), actor);
        UUID createdId = response.variant().id();
        assertEquals(0, response.inventory().availableQty());
        assertEquals(0, service.getMovements(createdId, 0, 20).getTotalElements());
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from inventory.outbox_events where aggregate_id = ?", Integer.class, createdId));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from catalog.outbox_events where aggregate_id = ?", Integer.class, productId));
    }

    @Test
    void combinedCreateJoinsOuterTransactionAndRollsBackAllWrites() {
        UUID creationOperation = UUID.randomUUID();
        java.util.concurrent.atomic.AtomicReference<UUID> createdId = new java.util.concurrent.atomic.AtomicReference<>();
        assertThrows(IllegalStateException.class, () ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    var response = createVariantWithStockService.create(productId,
                            new CreateVariantWithStockRequest("White", BigDecimal.TEN, 10, creationOperation), actor);
                    createdId.set(response.variant().id());
                    throw new IllegalStateException("Failure after all writes have flushed");
                }));
        assertEquals(0, jdbc.queryForObject("select count(*) from catalog.product_variants where id = ?",
                Integer.class, createdId.get()));
        assertEquals(0, jdbc.queryForObject("select count(*) from application.variant_creation_requests " +
                "where actor_id = ? and operation_id = ?", Integer.class, actor.id(), creationOperation));
        assertEquals(0, jdbc.queryForObject("select count(*) from inventory.items where variant_id = ?",
                Integer.class, createdId.get()));
        assertEquals(0, jdbc.queryForObject("select count(*) from inventory.movements where variant_id = ?",
                Integer.class, createdId.get()));
        assertEquals(0, jdbc.queryForObject("select count(*) from inventory.outbox_events where aggregate_id = ?",
                Integer.class, createdId.get()));
        assertEquals(0, jdbc.queryForObject("select count(*) from catalog.outbox_events where aggregate_id = ?",
                Integer.class, productId));
    }

    @Test
    void standaloneItemCreationStillRejectsDuplicatesAndNegativeInitialStock() {
        assertThrows(InventoryDomainException.class,
                () -> service.createItemWithStock(variantId, -1, UUID.randomUUID(), actor));
        var created = service.createItem(variantId);
        assertEquals(0, created.availableQty());
        assertEquals(InventoryErrorCode.ITEM_ALREADY_EXISTS, assertThrows(AppException.class,
                () -> service.createItemWithStock(variantId, 10, UUID.randomUUID(), actor)).getErrorCode());
        assertEquals(0, service.getItem(variantId).onHandQty());
    }

    @Test
    void creationRetryReplaysOriginalResponseWithoutReceivingStockTwice() {
        var request = new CreateVariantWithStockRequest("White", BigDecimal.TEN, 10, UUID.randomUUID());
        var first = createVariantWithStockService.create(productId, request, actor);
        service.adjust(first.variant().id(), new AdjustStockRequest(UUID.randomUUID(), -2, "Damaged", null, null), actor);
        var replay = createVariantWithStockService.create(productId, request, actor);
        assertEquals(first, replay);
        assertEquals(8, service.getItem(first.variant().id()).onHandQty());
        assertEquals(2, jdbc.queryForObject("select count(*) from catalog.product_variants where product_id = ?",
                Integer.class, productId)); // fixture Black + one White
        assertEquals(2, service.getMovements(first.variant().id(), 0, 20).getTotalElements());
    }

    private int count(String table, String column) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Integer.class, variantId);
    }
}
