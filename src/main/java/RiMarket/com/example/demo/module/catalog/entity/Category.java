package RiMarket.com.example.demo.module.catalog.entity;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.module.catalog.domain.policy.CategoryPolicy;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.*;

@Entity
@Table(name ="categories", schema ="catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, length = CategoryPolicy.MAX_NAME_LENGTH)
    private String name;

    //Many products belong to one category
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent")
    private Set<Category> subCategories = new HashSet<>();

    @Column(name = "created_at", updatable = false, nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    //Create new category
    public Category(String name, Category parent){
        this.name = CategoryPolicy.normalizeName(name);
        this.parent = parent;
        if (parent != null) {
            parent.subCategories.add(this);
        }
    }

    public void rename(String newName){
        this.name = CategoryPolicy.normalizeName(newName);
    }

    public void moveTo(Category newParent){
        if (Objects.equals(this.parent, newParent)) {
            return;
        }

        if (this.parent != null) {
            this.parent.subCategories.remove(this);
        }

        this.parent = newParent;

        if (newParent != null) {
            newParent.subCategories.add(this);
        }
    }

    public Set<Category> getSubCategories(){
        return Collections.unmodifiableSet(subCategories);
    }

    @PrePersist
    protected void onCreated(){
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdated(){
        this.updatedAt = ZonedDateTime.now();
    }
}
