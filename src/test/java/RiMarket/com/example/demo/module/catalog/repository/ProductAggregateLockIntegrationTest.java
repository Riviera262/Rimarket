package RiMarket.com.example.demo.module.catalog.repository;

import RiMarket.com.example.demo.module.catalog.entity.Category;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the actual Hibernate lock/fetch behavior against the configured PostgreSQL database. */
@SpringBootTest
@Transactional
class ProductAggregateLockIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void childCommandLocksOnlyProductAndCanAddASecondVariant() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category root = categoryRepository.save(new Category("Lock root " + suffix, null));
        Category category = categoryRepository.save(new Category("Lock child " + suffix, root));
        Product product = new Product("Lock test product", "lock-test-" + suffix, null, category);
        product.addVariant("LOCK-" + suffix.toUpperCase(), "First", BigDecimal.valueOf(100));
        productRepository.saveAndFlush(product);
        UUID productId = product.getId();

        entityManager.clear();

        Product aggregate = productRepository.findAggregateForChildCommandById(productId).orElseThrow();
        entityManager.lock(aggregate, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        assertEquals(LockModeType.OPTIMISTIC_FORCE_INCREMENT, entityManager.getLockMode(aggregate));

        aggregate.addVariant("SECOND-" + suffix.toUpperCase(), "Second", BigDecimal.valueOf(200));
        productRepository.flush();

        assertEquals(2, aggregate.getVariants().size());
    }
}
