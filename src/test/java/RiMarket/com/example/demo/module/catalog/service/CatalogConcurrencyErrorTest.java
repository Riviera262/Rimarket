package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogConcurrencyErrorTest {
    @Test
    void optimisticAndSerializationStyleConflictsReturn409() {
        var handler = new GlobalExceptionHandler();
        assertEquals(409, handler.handleOptimisticLockConflict(
                new OptimisticLockingFailureException("version conflict")).getStatusCode().value());
        assertEquals(409, handler.handleOptimisticLockConflict(
                new CannotAcquireLockException("serialization conflict")).getStatusCode().value());
    }
}
