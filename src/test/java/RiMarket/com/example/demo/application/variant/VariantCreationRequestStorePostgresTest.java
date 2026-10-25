package RiMarket.com.example.demo.application.variant;

import RiMarket.com.example.demo.module.catalog.dto.productVariant.ProductVariantResponse;
import RiMarket.com.example.demo.module.inventory.dto.InventoryItemResponse;
import RiMarket.com.example.demo.shared.exception.AppException;
import RiMarket.com.example.demo.shared.exception.CommonErrorCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, disposable PostgreSQL only. Never loads Spring Boot config or application credentials. */
@EnabledIfSystemProperty(named = "rimarket.idempotency-test-url",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/rimarket_idempotency_test")
class VariantCreationRequestStorePostgresTest {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static VariantCreationRequestStore store;
    static PlatformTransactionManager manager;
    final UUID actor = UUID.randomUUID();
    final UUID operation = UUID.randomUUID();
    final String hash = "a".repeat(64);

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean VariantCreationRequestStore store(JdbcTemplate template) {
            return new VariantCreationRequestStore(template);
        }
    }

    @BeforeAll
    static void initializeDisposableDatabase() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setUrl(System.getProperty("rimarket.idempotency-test-url"));
        source.setUsername("postgres"); // Local throwaway container uses trust; no stored credentials.
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> source);
        context.register(Config.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        manager = context.getBean(PlatformTransactionManager.class);
        store = context.getBean(VariantCreationRequestStore.class);
        // Minimal Catalog table for verifying the real manual migration, not a full application DB.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS catalog");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS catalog.product_variants (
                    id UUID PRIMARY KEY, product_id UUID NOT NULL, name VARCHAR(255) NOT NULL)
                """);
        String migration = Files.readString(Path.of("docs/database/2026-10-19-variant-creation-idempotency.sql"));
        jdbc.execute(migration);
        jdbc.execute(migration); // Safe rerun of this migration.
    }

    @AfterAll
    static void closeContext() { if (context != null) context.close(); }

    @AfterEach
    void removeOnlyOwnedRequests() {
        jdbc.update("DELETE FROM application.variant_creation_requests WHERE actor_id = ?", actor);
    }

    @Test
    void requiresOuterTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> store.claimOrReplay(actor, operation, hash));
    }

    @Test
    void roundTripsOriginalResponseIncludingZeroStockAndZonedTimestamps() {
        CreateVariantWithStockResponse response = response(0);
        tx().executeWithoutResult(status -> {
            assertTrue(store.claimOrReplay(actor, operation, hash).isEmpty());
            store.complete(actor, operation, response);
        });
        assertEquals(response, tx().execute(status -> store.claimOrReplay(actor, operation, hash).orElseThrow()));
        assertEquals(1, count());
    }

    @Test
    void differentHashConflictsWithoutChangingStoredResponse() {
        CreateVariantWithStockResponse response = response(10);
        tx().executeWithoutResult(status -> {
            store.claimOrReplay(actor, operation, hash);
            store.complete(actor, operation, response);
        });
        AppException failure = assertThrows(AppException.class, () -> tx().execute(status ->
                store.claimOrReplay(actor, operation, "b".repeat(64))));
        assertEquals(CommonErrorCode.IDEMPOTENCY_CONFLICT, failure.getErrorCode());
        assertEquals(response, tx().execute(status -> store.claimOrReplay(actor, operation, hash).orElseThrow()));
    }

    @Test
    void actorScopesTheKey() {
        UUID other = UUID.randomUUID();
        try {
            tx().executeWithoutResult(status -> {
                assertTrue(store.claimOrReplay(actor, operation, hash).isEmpty());
                store.complete(actor, operation, response(0));
            });
            tx().executeWithoutResult(status -> {
                assertTrue(store.claimOrReplay(other, operation, hash).isEmpty());
                store.complete(other, operation, response(0));
            });
        } finally {
            jdbc.update("DELETE FROM application.variant_creation_requests WHERE actor_id = ?", other);
        }
    }

    @Test
    void rollbackRemovesBothClaimAndCompletedResponseAndAllowsRetry() {
        assertThrows(IllegalStateException.class, () -> tx().executeWithoutResult(status -> {
            store.claimOrReplay(actor, operation, hash);
            store.complete(actor, operation, response(10));
            throw new IllegalStateException("Failure before commit");
        }));
        assertEquals(0, count());
        tx().executeWithoutResult(status -> {
            assertTrue(store.claimOrReplay(actor, operation, hash).isEmpty());
            store.complete(actor, operation, response(10));
        });
        assertEquals(1, count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentInsertWaitsThenReplaysOrTakesOverAfterRollback(boolean rollbackFirst) throws Exception {
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger secondPid = new AtomicInteger();
        CreateVariantWithStockResponse firstResponse = response(10);
        CreateVariantWithStockResponse retryResponse = response(10);
        try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first = workers.submit(() -> tx().executeWithoutResult(status -> {
                assertTrue(store.claimOrReplay(actor, operation, hash).isEmpty());
                claimed.countDown();
                await(release);
                store.complete(actor, operation, firstResponse);
                if (rollbackFirst) status.setRollbackOnly();
            }));
            Future<Optional<CreateVariantWithStockResponse>> second;
            try {
                assertTrue(claimed.await(5, TimeUnit.SECONDS));
                second = workers.submit(() -> tx().execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '8s'");
                    secondPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    Optional<CreateVariantWithStockResponse> result = store.claimOrReplay(actor, operation, hash);
                    if (result.isEmpty()) store.complete(actor, operation, retryResponse);
                    return result;
                }));
                // Observe an actual PostgreSQL lock wait rather than assuming thread overlap.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline && !waiting) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE pid = ? AND wait_event_type = 'Lock')
                            """, Boolean.class, secondPid.get()));
                    if (!waiting) Thread.sleep(20);
                }
                assertTrue(waiting, "Second INSERT should wait on the first transaction's unique key");
            } finally {
                release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            Optional<CreateVariantWithStockResponse> replay = second.get(10, TimeUnit.SECONDS);
            assertEquals(rollbackFirst ? Optional.empty() : Optional.of(firstResponse), replay);
            assertEquals(rollbackFirst ? retryResponse : firstResponse,
                    tx().execute(status -> store.claimOrReplay(actor, operation, hash).orElseThrow()));
            assertEquals(1, count());
        }
    }

    @Test
    void databaseUniqueNameProtectsInsertsAndUpdatesWithinOneProduct() {
        UUID product = UUID.randomUUID();
        UUID otherProduct = UUID.randomUUID();
        UUID white = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO catalog.product_variants VALUES (?, ?, ?)", UUID.randomUUID(), product, "Black");
            jdbc.update("INSERT INTO catalog.product_variants VALUES (?, ?, ?)", white, product, "White");
            assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                    "INSERT INTO catalog.product_variants VALUES (?, ?, ?)", UUID.randomUUID(), product, "Black"));
            assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> jdbc.update(
                    "UPDATE catalog.product_variants SET name = 'Black' WHERE id = ?", white));
            assertDoesNotThrow(() -> jdbc.update("INSERT INTO catalog.product_variants VALUES (?, ?, ?)",
                    UUID.randomUUID(), otherProduct, "Black"));
        } finally {
            jdbc.update("DELETE FROM catalog.product_variants WHERE product_id IN (?, ?)", product, otherProduct);
        }
    }

    private TransactionTemplate tx() {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(15);
        return template;
    }

    private int count() {
        return jdbc.queryForObject("SELECT count(*) FROM application.variant_creation_requests WHERE actor_id = ?",
                Integer.class, actor);
    }

    private CreateVariantWithStockResponse response(int quantity) {
        UUID variant = UUID.randomUUID();
        ZonedDateTime time = ZonedDateTime.parse("2026-10-19T10:20:30+07:00[Asia/Ho_Chi_Minh]");
        return new CreateVariantWithStockResponse(
                new ProductVariantResponse(variant, "SKU", "Black", BigDecimal.TEN, 0, true),
                new InventoryItemResponse(variant, "SKU", "Product", "Black", quantity, 0, quantity, 1L, time, time));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for competing request");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
