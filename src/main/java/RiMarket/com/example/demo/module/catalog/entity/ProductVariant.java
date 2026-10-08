package RiMarket.com.example.demo.module.catalog.entity;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.policy.VariantImagePolicy;
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
@Table(name = "product_variants", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, updatable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal price;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @OneToMany(
            mappedBy = "variant",
            fetch = FetchType.LAZY,
            cascade = CascadeType.PERSIST,
            orphanRemoval = true
    )
    @OrderBy("sortOrder ASC")
    @Getter(AccessLevel.NONE)
    private List<VariantImage> images = new ArrayList<>();

    @Column(name = "created_at", updatable = false, nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    ProductVariant(Product product, String sku, String name, BigDecimal price, int sortOrder){
        if (product == null){
            throw new CatalogDomainException(CatalogDomainViolation.VARIANT_NOT_IN_PRODUCT);
        }
        this.product = product;
        this.sku = VariantPolicy.normalizeSku(sku);
        this.name = VariantPolicy.normalizeName(name);
        this.price = validatePrice(price);
        this.sortOrder = VariantPolicy.validateSortOrder(sortOrder);
        this.active = true;
    }

    void rename(String newName){
        this.name = VariantPolicy.normalizeName(newName);
    }

    void changePrice(BigDecimal newPrice){
        this.price = validatePrice(newPrice);
    }

    void activate(){
        this.active = true;
    }

    void deactivate(){
        this.active = false;
    }

    void changeSortOrder(int sortOrder){
        this.sortOrder = VariantPolicy.validateSortOrder(sortOrder);
    }


    //================================================================
    // VARIANT-IMAGE
    //================================================================
    VariantImage addImage(String publicId, String url) {
        ensureCanAddImage();

        int sortOrder = images.size();
        VariantImage image = new VariantImage(this, publicId, url, sortOrder);
        images.add(image);

        return image;
    }

    public List<VariantImage> getImages(){
        return Collections.unmodifiableList(images);
    }

    VariantImage removeImage(UUID imageId){
        VariantImage image = findImage(imageId);

        images.remove(image);
        normalizeSortOrder();

        return image;
    }

    VariantImage replaceImage(UUID imageId, String publicId, String url) {
        VariantImage image = findImage(imageId);
        image.replaceStoredImage(publicId, url);

        return image;
    }

    void ensureCanAddImage() {
        ensureCanAddImages(1);
    }

    void ensureCanAddImages(int additionalImageCount) {
        if (additionalImageCount <= 0) {
            throw new IllegalArgumentException("Additional image count must be positive");
        }

        if (images.size() + additionalImageCount > VariantImagePolicy.MAX_IMAGES) {
            throw new CatalogDomainException(CatalogDomainViolation.VARIANT_IMAGE_LIMIT_EXCEEDED);
        }
    }

    VariantImage getImage(UUID imageId) {
        return findImage(imageId);
    }

    void reorderImages(List<UUID> orderedImageIds) {
        Set<UUID> currentImageIds = images.stream()
                .map(VariantImage::getId)
                .collect(Collectors.toSet());

        //Check current list of images from request and list of images in db
        VariantImagePolicy.validateReorder(currentImageIds, orderedImageIds);

        Map<UUID, VariantImage> imageById = images.stream()
                .collect(Collectors.toMap(VariantImage::getId, Function.identity()));


        for (int index = 0; index < orderedImageIds.size(); index++) {
            imageById.get(orderedImageIds.get(index)).changeSortOrder(index);
        }
        images.sort(Comparator.comparingInt(VariantImage::getSortOrder));
    }


    //HELPER
    private static BigDecimal validatePrice(BigDecimal price){
        if (price == null){
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_PRICE);
        }

        if (price.signum() < 0){
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_PRICE);
        }

        if (price.stripTrailingZeros().scale() > 0){
            throw new CatalogDomainException(CatalogDomainViolation.INVALID_VARIANT_PRICE);
        }

        return price.setScale(0);
    }

    private VariantImage findImage(UUID imageId){
        return images.stream()
                .filter(image -> Objects.equals(image.getId(), imageId))
                .findFirst()
                .orElseThrow(() -> new CatalogDomainException(CatalogDomainViolation.VARIANT_IMAGE_NOT_IN_VARIANT));
    }

    private void normalizeSortOrder(){
        images.sort(Comparator.comparingInt(VariantImage::getSortOrder));

        for (int index = 0; index < images.size(); index++){
            images.get(index).changeSortOrder(index);
        }
    }

    @PrePersist
    protected void onCreate(){
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = ZonedDateTime.now();
    }
}
