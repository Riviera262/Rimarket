package RiMarket.com.example.demo.application.variant;

import RiMarket.com.example.demo.shared.exception.AppException;
import RiMarket.com.example.demo.shared.exception.CommonErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Workflow persistence, not a Catalog or Inventory aggregate repository. */
@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class VariantCreationRequestStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

    /** Empty = this transaction owns a new/expired key; present = replay an active result. */
    public Optional<CreateVariantWithStockResponse> claimOrReplay(UUID actorId, UUID operationId, String hash) {
        // The unique key coordinates concurrent callers. An expired row is atomically reclaimed.
        int inserted = jdbc.update("""
                INSERT INTO application.variant_creation_requests
                    (actor_id, operation_id, request_hash, expires_at)
                VALUES (?, ?, ?, statement_timestamp() + INTERVAL '24 hours')
                ON CONFLICT (actor_id, operation_id) DO UPDATE SET
                    request_hash = EXCLUDED.request_hash,
                    response_json = NULL,
                    created_at = statement_timestamp(),
                    completed_at = NULL,
                    expires_at = EXCLUDED.expires_at
                WHERE application.variant_creation_requests.expires_at <= statement_timestamp()
                """, actorId, operationId, hash);
        if (inserted == 1){
            return Optional.empty();
        }

        // Separate statement: READ COMMITTED sees the winner's committed response after the wait.
        CreateVariantWithStockResponse response = jdbc.queryForObject("""
                SELECT request_hash, response_json::text
                FROM application.variant_creation_requests
                WHERE actor_id = ? AND operation_id = ?
                """, (row, index) -> {
            if (!hash.equals(row.getString("request_hash"))) {
                throw new AppException(CommonErrorCode.IDEMPOTENCY_CONFLICT);
            }

            String json = row.getString("response_json");
            if (json == null){
                throw new IllegalStateException("Committed creation request has no result");
            }

            return readResponse(json);
        }, actorId, operationId);
        return Optional.of(java.util.Objects.requireNonNull(response, "Stored creation response is required"));
    }

    public void complete(UUID actorId, UUID operationId, CreateVariantWithStockResponse response) {
        int updated = jdbc.update("""
                UPDATE application.variant_creation_requests
                SET response_json = CAST(? AS jsonb), completed_at = CURRENT_TIMESTAMP
                WHERE actor_id = ? AND operation_id = ? AND response_json IS NULL
                """, writeResponse(response), actorId, operationId);
        if (updated != 1){
            throw new IllegalStateException("Creation request was not claimed or is already complete");
        }
    }

    private String writeResponse(CreateVariantWithStockResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot store variant creation result", exception);
        }
    }

    private CreateVariantWithStockResponse readResponse(String json) {
        try {
            return mapper.readValue(json, CreateVariantWithStockResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read variant creation result", exception);
        }
    }
}
