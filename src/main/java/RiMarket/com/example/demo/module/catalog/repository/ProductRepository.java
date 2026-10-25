package RiMarket.com.example.demo.module.catalog.repository;

import RiMarket.com.example.demo.module.catalog.api.CatalogVariantImageSnapshot;
import RiMarket.com.example.demo.module.catalog.api.CatalogVariantSnapshot;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.entity.ProductVariant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    @Query("""
        SELECT new RiMarket.com.example.demo.module.catalog.api.CatalogVariantSnapshot(
            v.id, v.sku, v.product.name, v.name)
        FROM ProductVariant v WHERE v.id = :variantId
    """)
    Optional<CatalogVariantSnapshot> findVariantSnapshot(@Param("variantId") UUID variantId);

    // Scalar projection, no collection fetch join; safe to call after paging Inventory items.
    @Query("""
        SELECT new RiMarket.com.example.demo.module.catalog.api.CatalogVariantImageSnapshot(
            image.variant.id, image.id, image.url, image.sortOrder)
        FROM VariantImage image
        WHERE image.variant.id IN :variantIds
        ORDER BY image.variant.id, image.sortOrder, image.id
    """)
    List<CatalogVariantImageSnapshot> findVariantImageSnapshots(@Param("variantIds") Collection<UUID> variantIds);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsByCategoryId(UUID categoryId);

    Page<Product> findByActiveTrue(Pageable pageable);

    //List of active Product
    Page<Product> findByCategory_IdAndActiveTrue(UUID categoryId, Pageable pageable);

    //Find Product By ProductId
    @EntityGraph(attributePaths = {"variants"})
    @Query("""
        SELECT p FROM Product p WHERE p.id = :productId
    """)
    Optional<Product> findAggregateById(@Param("productId") UUID productId);

    @EntityGraph(attributePaths = {"variants"})
    @Query("""
        SELECT p FROM Product p WHERE p.id = :productId
    """)
    Optional<Product> findAggregateForChildCommandById(@Param("productId") UUID productId);

    //Public detail
    @EntityGraph(attributePaths = {"variants"})
    @Query("""
        SELECT p FROM Product p WHERE LOWER(p.slug) = LOWER(:slug) AND p.active = true
    """)
    Optional<Product> findDetailBySlug(@Param("slug") String slug);

    boolean existsByVariantsSkuIgnoreCase(String sku);

    // Fetch-plan helper: call after locking/fetching Product + variants in the same transaction.
    // Variants and images stay in separate fetch queries to avoid fetching two List bags at once.
    @Query("""
        SELECT DISTINCT variant
        FROM ProductVariant variant
        LEFT JOIN FETCH variant.images
        WHERE variant.product.id = :productId
    """)
    List<ProductVariant> findAllWithImagesByProductId(@Param("productId") UUID productId);
}
