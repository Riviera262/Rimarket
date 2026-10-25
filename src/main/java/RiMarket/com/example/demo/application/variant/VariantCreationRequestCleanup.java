package RiMarket.com.example.demo.application.variant;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically removes expired idempotency receipts in bounded batches. */
@Component
@RequiredArgsConstructor
public class VariantCreationRequestCleanup {
    private static final int BATCH_SIZE = 1_000;
    private final JdbcTemplate jdbc;

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void deleteExpiredBatch() {
        jdbc.update("""
                WITH expired AS (
                    SELECT actor_id, operation_id
                    FROM application.variant_creation_requests
                    WHERE expires_at <= statement_timestamp()
                    ORDER BY expires_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM application.variant_creation_requests AS request
                USING expired
                WHERE request.actor_id = expired.actor_id
                  AND request.operation_id = expired.operation_id
                """, BATCH_SIZE);
    }
}
