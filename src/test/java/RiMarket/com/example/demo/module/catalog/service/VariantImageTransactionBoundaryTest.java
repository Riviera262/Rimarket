package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.entity.*;
import RiMarket.com.example.demo.module.catalog.mapper.VariantImageMapper;
import RiMarket.com.example.demo.module.catalog.port.*;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Exercises real Spring transaction advice with a fake transaction manager, not a database. */
class VariantImageTransactionBoundaryTest {
    @Test
    void commitFailureAfterSuccessfulAttachStillCompensatesUpload() {
        var repository = mock(ProductRepository.class);
        var storage = mock(ImageStorage.class);
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Category root = new Category("Devices", null);
        Product product = new Product("Laptop", "laptop", null, new Category("Laptops", root));
        var variant = product.addVariant("SKU", "Black", BigDecimal.ONE);
        ReflectionTestUtils.setField(variant, "id", variantId);
        when(repository.findAggregateById(productId)).thenReturn(Optional.of(product));
        when(repository.findAggregateForChildCommandById(productId)).thenReturn(Optional.of(product));
        when(storage.upload(any(), anyString())).thenReturn(new StoredImage("new-asset", "https://cdn.test/new"));

        var manager = new CommitFailureManager();
        var commandTarget = new VariantImageCommandService(repository, new VariantImageMapper());
        ReflectionTestUtils.setField(commandTarget, "entityManager", mock(jakarta.persistence.EntityManager.class));
        var commands = proxy(commandTarget, manager);
        var service = proxy(new VariantImageService(storage, commands), manager);
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});

        assertThrows(OptimisticLockingFailureException.class,
                () -> service.uploadImage(productId, variantId, file));
        assertEquals(1, manager.successfulReadCommits);
        assertEquals(1, manager.failedWriteCommits);
        verify(repository).flush();
        verify(storage).delete("new-asset");
    }

    @Test
    void orchestrationRejectsAmbientTransactionBeforeCallingStorage() {
        var storage = mock(ImageStorage.class);
        var commands = mock(VariantImageCommandService.class);
        var manager = new CommitFailureManager();
        var service = proxy(new VariantImageService(storage, commands), manager);
        var tx = new TransactionTemplate(manager);
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        assertThrows(IllegalTransactionStateException.class, () -> tx.executeWithoutResult(status ->
                service.uploadImage(UUID.randomUUID(), UUID.randomUUID(), file)));
        verifyNoInteractions(storage, commands);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, PlatformTransactionManager manager) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (T) factory.getProxy();
    }

    private static class CommitFailureManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        int successfulReadCommits;
        int failedWriteCommits;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object transaction) { return active.get(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { active.set(true); }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (status.isReadOnly()) {
                successfulReadCommits++;
            } else {
                failedWriteCommits++;
                throw new OptimisticLockingFailureException("Simulated root-version conflict at commit");
            }
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
        @Override protected void doCleanupAfterCompletion(Object transaction) { active.remove(); }
    }
}
