package RiMarket.com.example.demo.module.catalog.service;

import RiMarket.com.example.demo.module.catalog.domain.identifier.SlugGenerator;
import RiMarket.com.example.demo.module.catalog.dto.product.CreateProductRequest;
import RiMarket.com.example.demo.module.catalog.dto.product.ProductDetailResponse;
import RiMarket.com.example.demo.module.catalog.dto.product.ProductSummaryResponse;
import RiMarket.com.example.demo.module.catalog.dto.product.UpdateProductRequest;
import RiMarket.com.example.demo.module.catalog.entity.Category;
import RiMarket.com.example.demo.module.catalog.entity.Product;
import RiMarket.com.example.demo.module.catalog.exception.CatalogErrorCode;
import RiMarket.com.example.demo.module.catalog.mapper.ProductMapper;
import RiMarket.com.example.demo.module.catalog.repository.CategoryRepository;
import RiMarket.com.example.demo.module.catalog.repository.ProductRepository;
import RiMarket.com.example.demo.shared.exception.AppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
    @PersistenceContext
    private EntityManager entityManager;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final SlugGenerator slugGenerator;

    //=======================================================
    // GET
    //=======================================================

    @Transactional(readOnly = true)
    public ProductDetailResponse getProductDetailBySlug(String slug){
        Product product = productRepository.findDetailBySlug(slug)
                .orElseThrow(()-> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND));

        // Initializes images on the managed variants loaded by the first query.
        productRepository.findAllWithImagesByProductId(product.getId());

        return productMapper.toDetailResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getProducts(Pageable pageable){
        Page<Product> products = productRepository.findByActiveTrue(pageable);

        return products.map(productMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<ProductSummaryResponse> getProductsByCategory(UUID categoryId, Pageable pageable){
        Page<Product> products = productRepository.findByCategory_IdAndActiveTrue(categoryId, pageable);

        return products.map(productMapper::toSummaryResponse);
    }

    //=======================================================
    // WRITE
    //=======================================================

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ProductSummaryResponse createProduct(CreateProductRequest request){
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_NOT_FOUND));

        String slug = slugGenerator.generate(request.name());

        if (productRepository.existsBySlugIgnoreCase(slug)){
            slug = slugGenerator.appendEntropy(slug);
        }

        Product product = new Product(
                request.name(),
                slug,
                request.description(),
                category
        );

        Product saved = productRepository.save(product);

        return productMapper.toSummaryResponse(saved);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ProductSummaryResponse updateProduct(UUID productId, UpdateProductRequest request){
        Product product = getProduct(productId);
        Category targetCategory = product.getCategory();
        if (!product.getCategory().getId().equals(request.categoryId())){
            targetCategory = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new AppException(CatalogErrorCode.CATEGORY_NOT_FOUND));
        }

        // Validate category placement before mutating the rest of the Product.
        product.moveToCategory(targetCategory);
        product.rename(request.name());
        product.changeDescription(request.description());

        return productMapper.toSummaryResponse(product);
    }

    @Transactional
    public void activateProduct(UUID productId){
        Product product = getProductAggregateForCommand(productId);

        product.activate();
    }

    @Transactional
    public void deactivateProduct(UUID productId){
        Product product = getProductAggregateForCommand(productId);

        product.deactivate();
    }

    @Transactional
    public void deleteDraftProduct(UUID productId){
        Product product = getProductAggregate(productId);

        if (product.isActive()){
            throw new AppException(CatalogErrorCode.ACTIVE_PRODUCT_CANNOT_BE_DELETED);
        }

        if (!product.getVariants().isEmpty()){
            throw new AppException(CatalogErrorCode.PRODUCT_WITH_VARIANTS_CANNOT_BE_DELETED);
        }

        productRepository.delete(product);
    }


    private Product getProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }

    private Product getProductAggregate(UUID productId) {
        return productRepository.findAggregateById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }

    private Product getProductAggregateForCommand(UUID productId) {
        Product product = productRepository.findAggregateForChildCommandById(productId)
                .orElseThrow(() -> new AppException(CatalogErrorCode.PRODUCT_NOT_FOUND));

        // Lock only the versioned aggregate root; variants remain unversioned children.
        entityManager.lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        return product;
    }
}
