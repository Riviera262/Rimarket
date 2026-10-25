package RiMarket.com.example.demo.module.catalog.exception;

import RiMarket.com.example.demo.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CatalogErrorCode implements ErrorCode {
    // =========================================================
    // CATEGORY
    // =========================================================
    CATEGORY_NOT_FOUND(
            "CATALOG_001",
            "Category not found",
            HttpStatus.NOT_FOUND
    ),

    PARENT_CATEGORY_NOT_FOUND(
            "CATALOG_002",
            "Parent category not found",
            HttpStatus.NOT_FOUND
    ),

    INVALID_CATEGORY_NAME(
            "CATALOG_003",
            "Category name is invalid",
            HttpStatus.BAD_REQUEST
    ),

    CATEGORY_ALREADY_EXISTS(
            "CATALOG_004",
            "A category with the same name already exists under this parent",
            HttpStatus.CONFLICT
    ),

    CATEGORY_DEPTH_EXCEEDED(
            "CATALOG_005",
            "Category hierarchy exceeds the maximum depth",
            HttpStatus.CONFLICT
    ),

    CATEGORY_CIRCULAR_DEPENDENCY(
            "CATALOG_006",
            "Category hierarchy cannot contain a circular dependency",
            HttpStatus.CONFLICT
    ),

    CATEGORY_HAS_CHILDREN(
            "CATALOG_007",
            "Category cannot be deleted while it has child categories",
            HttpStatus.CONFLICT
    ),

    CATEGORY_HAS_PRODUCTS(
            "CATALOG_008",
            "Category cannot be deleted while it contains products",
            HttpStatus.CONFLICT
    ),

    CATEGORY_TREE_CORRUPTED(
            "CATALOG_009",
            "Category hierarchy contains invalid persisted data",
            HttpStatus.INTERNAL_SERVER_ERROR
    ),



    // =========================================================
    // PRODUCT
    // =========================================================
    PRODUCT_NOT_FOUND(
            "CATALOG_101",
            "Product not found",
            HttpStatus.NOT_FOUND
    ),

    INVALID_PRODUCT_NAME(
            "CATALOG_102",
            "Product name is invalid",
            HttpStatus.BAD_REQUEST
    ),

    INVALID_PRODUCT_SLUG(
            "CATALOG_103",
            "Product slug is invalid",
            HttpStatus.BAD_REQUEST
    ),

    PRODUCT_SLUG_ALREADY_EXISTS(
            "CATALOG_104",
            "Product slug already exists",
            HttpStatus.CONFLICT
    ),

    PRODUCT_CATEGORY_REQUIRED(
            "CATALOG_105",
            "Product must belong to a category",
            HttpStatus.BAD_REQUEST
    ),

    PRODUCT_REQUIRES_ACTIVE_VARIANT(
            "CATALOG_106",
            "Product requires at least one active variant before it can be activated",
            HttpStatus.CONFLICT
    ),

    ACTIVE_PRODUCT_CANNOT_BE_DELETED(
            "CATALOG_107",
            "Active product cannot be deleted",
            HttpStatus.CONFLICT
    ),

    PRODUCT_WITH_VARIANTS_CANNOT_BE_DELETED(
            "CATALOG_108",
            "Product cannot be deleted while it has variants",
            HttpStatus.CONFLICT
    ),
    PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT(
            "CATALOG_109",
            "Category root cannot contain any products",
            HttpStatus.BAD_REQUEST
    ),

    // =========================================================
    // PRODUCT VARIANT
    // =========================================================
    PRODUCT_VARIANT_NOT_FOUND(
            "CATALOG_201",
            "Product variant not found",
            HttpStatus.NOT_FOUND
    ),

    INVALID_VARIANT_NAME(
            "CATALOG_202",
            "Product variant name is invalid",
            HttpStatus.BAD_REQUEST
    ),

    INVALID_VARIANT_PRICE(
            "CATALOG_203",
            "Product variant price is invalid",
            HttpStatus.BAD_REQUEST
    ),

    INVALID_SKU(
            "CATALOG_204",
            "SKU is invalid",
            HttpStatus.BAD_REQUEST
    ),

    SKU_ALREADY_EXISTS(
            "CATALOG_205",
            "SKU already exists",
            HttpStatus.CONFLICT
    ),

    LAST_ACTIVE_VARIANT_CANNOT_BE_DEACTIVATED(
            "CATALOG_206",
            "The last active variant of an active product cannot be deactivated",
            HttpStatus.CONFLICT
    ),

    INVALID_VARIANT_ORDER(
            "CATALOG_207",
            "Variant sort order is invalid",
            HttpStatus.BAD_REQUEST
    ),

    DUPLICATE_VARIANT_NAME(
            "CATALOG_208",
            "A variant with this name already exists in this product",
            HttpStatus.CONFLICT
    ),


    // =========================================================
    // VARIANT IMAGE
    // =========================================================
    VARIANT_IMAGE_NOT_FOUND(
            "CATALOG_301",
            "Variant image not found",
            HttpStatus.NOT_FOUND
    ),

    INVALID_VARIANT_IMAGE_URL(
            "CATALOG_302",
            "Variant image URL is invalid",
            HttpStatus.BAD_REQUEST
    ),

    INVALID_VARIANT_IMAGE_PUBLIC_ID(
            "CATALOG_303",
            "Variant image public ID is invalid",
            HttpStatus.BAD_REQUEST
    ),

    INVALID_VARIANT_IMAGE_SORT_ORDER(
            "CATALOG_304",
            "Variant image sort order is invalid",
            HttpStatus.BAD_REQUEST
    ),

    VARIANT_IMAGE_REQUIRES_VARIANT(
            "CATALOG_305",
            "Variant image must belong to a variant",
            HttpStatus.BAD_REQUEST
    ),

    VARIANT_IMAGE_LIMIT_EXCEEDED(
            "CATALOG_306",
            "Variant image limit has been exceeded",
            HttpStatus.CONFLICT
    ),

    INVALID_VARIANT_IMAGE_ORDER(
            "CATALOG_307",
            "Variant image order is invalid",
            HttpStatus.BAD_REQUEST
    );


    private final String code;
    private final String message;
    private final HttpStatus status;
}
