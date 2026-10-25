package RiMarket.com.example.demo.application.variant;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class VariantCreationFingerprintTest {
    private final UUID product = UUID.randomUUID();

    @Test
    void normalizesWhitespaceAndEquivalentPricesButNotCase() {
        assertEquals(hash(product, "Black", "10", 0), hash(product, " Black ", "10.00", 0));
        assertNotEquals(hash(product, "Black", "10", 0), hash(product, "black", "10", 0));
        assertEquals(64, hash(product, "Black", "10", 0).length());
    }

    @Test
    void includesProductNamePriceAndQuantityButNotTheKeyItself() {
        String original = hash(product, "Black", "10", 0);
        assertNotEquals(original, hash(UUID.randomUUID(), "Black", "10", 0));
        assertNotEquals(original, hash(product, "White", "10", 0));
        assertNotEquals(original, hash(product, "Black", "11", 0));
        assertNotEquals(original, hash(product, "Black", "10", 1));
        // Each call generates a different key; it belongs to the DB primary key, not the hash.
        assertEquals(original, hash(product, "Black", "10", 0));
    }

    private String hash(UUID productId, String name, String price, int quantity) {
        return VariantCreationFingerprint.of(productId,
                new CreateVariantWithStockRequest(name, new BigDecimal(price), quantity, UUID.randomUUID()));
    }
}
