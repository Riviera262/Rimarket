package RiMarket.com.example.demo.module.catalog.entity;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.policy.VariantImagePolicy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "variant_images", schema = "catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VariantImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "public_id", nullable = false, length = 255)
    private String publicId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;


    VariantImage(ProductVariant variant, String publicId, String url, int sortOrder) {
        if (variant == null) {
            throw new CatalogDomainException(CatalogDomainViolation.VARIANT_IMAGE_REQUIRES_VARIANT);
        }

        this.variant = variant;
        this.publicId = VariantImagePolicy.normalizePublicId(publicId);
        this.url = VariantImagePolicy.normalizeUrl(url);
        this.sortOrder = VariantImagePolicy.validateSortOrder(sortOrder);
    }

    void changeSortOrder(int sortOrder){
        this.sortOrder = VariantImagePolicy.validateSortOrder(sortOrder);
    }

    void replaceStoredImage(String publicId, String url) {
        String normalizedPublicId = VariantImagePolicy.normalizePublicId(publicId);
        String normalizedUrl = VariantImagePolicy.normalizeUrl(url);

        // Apply both values only after all validation succeeds.
        this.publicId = normalizedPublicId;
        this.url = normalizedUrl;
    }

    @PrePersist
    protected void onCreated() {
        createdAt = ZonedDateTime.now();
    }
}
