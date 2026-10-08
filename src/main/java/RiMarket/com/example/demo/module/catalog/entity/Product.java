package RiMarket.com.example.demo.module.catalog.entity;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.policy.ProductPolicy;
import RiMarket.com.example.demo.module.catalog.domain.policy.VariantPolicy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Table(name = "products", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    // Many products belong to one category
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 255)
    private String name;

    // Stable public URL key. Renaming a Product must not silently break old URLs.
    @Column(nullable = false, updatable = false, length = 255)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "product", cascade = CascadeType.PERSIST)
    @OrderBy("sortOrder ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @Column(name = "created_at", updatable = false, nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public Product(String name, String slug, String description, Category category){
        this.name = ProductPolicy.normalizeName(name);
        this.slug = ProductPolicy.normalizeSlug(slug);
        this.description = description;
        this.category = requireAssignableCategory(category);
    }

    public void rename(String newName){
        this.name = ProductPolicy.normalizeName(newName);
    }

    public void changeDescription(String newDescription){
        this.description = newDescription;
    }

    public void moveToCategory(Category newCategory){
        this.category = requireAssignableCategory(newCategory);
    }

    public void activate(){
        boolean hasActiveVariant = variants.stream().anyMatch(ProductVariant::isActive);
        if (!hasActiveVariant){
            throw new CatalogDomainException(CatalogDomainViolation.PRODUCT_REQUIRES_ACTIVE_VARIANT);
        }
        this.active = true;
    }

    public void deactivate(){
        this.active = false;
    }

    //================================================================
    // VARIANT
    //================================================================
    public ProductVariant addVariant(String sku, String variantName, BigDecimal price){
        boolean firstVariant = variants.isEmpty();

        int sortOrder = variants.stream()
                .mapToInt(ProductVariant::getSortOrder)
                .max()
                .orElse(-1) + 1;

        ProductVariant variant = new ProductVariant(
                this,
                sku,
                variantName,
                price,
                sortOrder
        );
        variants.add(variant);

        if (firstVariant) {
            activate();
        }

        return variant;
    }

    public ProductVariant updateVariant(UUID variantId, String newName, BigDecimal newPrice){
        ProductVariant variant = findVariant(variantId);

        variant.rename(newName);
        variant.changePrice(newPrice);

        return variant;
    }

    public void activateVariant(UUID variantId){
        ProductVariant variant = findVariant(variantId);
        variant.activate();
    }

    public void deactivateVariant(UUID variantId){
        ProductVariant variant = findVariant(variantId);

        if (active && variant.isActive()){
            long activeCount = variants.stream().filter(ProductVariant::isActive).count();

            if (activeCount<=1){
                throw new CatalogDomainException(CatalogDomainViolation.LAST_ACTIVE_VARIANT_CANNOT_BE_DEACTIVATED);
            }
        }

        variant.deactivate();
    }

    //Reorder every variants in a product
    public void reorderVariants(List<UUID> orderedVariantIds){
        Set<UUID> currentVariantIds = variants.stream()
                .map(ProductVariant::getId)
                .collect(Collectors.toSet());

        VariantPolicy.validateReorder(currentVariantIds, orderedVariantIds);

        Map<UUID, ProductVariant> variantById = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        for (int index = 0; index < orderedVariantIds.size(); index++){
            variantById.get(orderedVariantIds.get(index)).changeSortOrder(index);
        }
        variants.sort(Comparator.comparingInt(ProductVariant::getSortOrder));
    }

    private ProductVariant findVariant(UUID variantId){
        return variants.stream()
                .filter(v -> Objects.equals(v.getId(), variantId))
                .findFirst()
                .orElseThrow(() -> new CatalogDomainException(CatalogDomainViolation.VARIANT_NOT_IN_PRODUCT));
    }

    public List<ProductVariant> getVariants( ){
        return Collections.unmodifiableList(variants);
    }


    //================================================================
    // VARIANT-IMAGE
    //================================================================
    public VariantImage addVariantImage(UUID variantId, String publicId, String url) {

        ProductVariant variant = findVariant(variantId);

        return variant.addImage(publicId, url);
    }

    public VariantImage removeVariantImage(UUID variantId, UUID imageId){
        ProductVariant variant = findVariant(variantId);

        return variant.removeImage(imageId);
    }

    public VariantImage replaceVariantImage(UUID variantId, UUID imageId, String publicId, String url) {
        ProductVariant variant = findVariant(variantId);

        return variant.replaceImage(imageId, publicId, url);
    }

    public void ensureCanAddVariantImage(UUID variantId){
        ensureCanAddVariantImages(variantId, 1);
    }

    public void ensureCanAddVariantImages(UUID variantId, int additionalImageCount){
        ProductVariant variant = findVariant(variantId);

        variant.ensureCanAddImages(additionalImageCount);
    }

    public void reorderVariantImages(UUID variantId, List<UUID> orderedImageIds) {

        ProductVariant variant = findVariant(variantId);
        variant.reorderImages(orderedImageIds);
    }

    public List<VariantImage> getVariantImages(UUID variantId) {
        ProductVariant variant = findVariant(variantId);

        return variant.getImages();
    }

    public VariantImage getVariantImage(UUID variantId, UUID imageId) {
        ProductVariant variant = findVariant(variantId);

        return variant.getImage(imageId);
    }

    private static Category requireAssignableCategory(Category category) {
        if (category == null) {
            throw new CatalogDomainException(CatalogDomainViolation.PRODUCT_CATEGORY_REQUIRED);
        }
        if (category.getParent() == null) {
            throw new CatalogDomainException(CatalogDomainViolation.PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT);
        }
        return category;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
