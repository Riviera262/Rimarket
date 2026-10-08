package RiMarket.com.example.demo.module.catalog.exception;

import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainException;
import RiMarket.com.example.demo.module.catalog.domain.exception.CatalogDomainViolation;
import RiMarket.com.example.demo.shared.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "RiMarket.com.example.demo.module.catalog")
public class CatalogExceptionHandler {

    @ExceptionHandler(CatalogDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(
            CatalogDomainException exception
    ) {

        CatalogErrorCode error = mapViolation(exception.getViolation());

        ErrorResponse response = new ErrorResponse(
                    error.getStatus().value(),
                    error.getCode(),
                    error.getMessage()
        );

        return ResponseEntity
                .status(error.getStatus())
                .body(response);
    }

    private CatalogErrorCode mapViolation(
            CatalogDomainViolation violation
    ) {

        return switch (violation) {

            // CATEGORY
            case INVALID_CATEGORY_NAME ->
                    CatalogErrorCode.INVALID_CATEGORY_NAME;

            case CATEGORY_DEPTH_EXCEEDED ->
                    CatalogErrorCode.CATEGORY_DEPTH_EXCEEDED;

            case CATEGORY_CIRCULAR_DEPENDENCY ->
                    CatalogErrorCode.CATEGORY_CIRCULAR_DEPENDENCY;

            case CATEGORY_DUPLICATE_SIBLING_NAME ->
                    CatalogErrorCode.CATEGORY_ALREADY_EXISTS;

            case CATEGORY_HAS_CHILDREN ->
                    CatalogErrorCode.CATEGORY_HAS_CHILDREN;

            case CATEGORY_HAS_PRODUCTS ->
                    CatalogErrorCode.CATEGORY_HAS_PRODUCTS;

            case CATEGORY_TREE_CORRUPTED ->
                    CatalogErrorCode.CATEGORY_TREE_CORRUPTED;



            // PRODUCT
            case INVALID_PRODUCT_NAME ->
                    CatalogErrorCode.INVALID_PRODUCT_NAME;

            case INVALID_PRODUCT_SLUG ->
                    CatalogErrorCode.INVALID_PRODUCT_SLUG;

            case PRODUCT_CATEGORY_REQUIRED ->
                    CatalogErrorCode.PRODUCT_CATEGORY_REQUIRED;

            case PRODUCT_REQUIRES_ACTIVE_VARIANT ->
                    CatalogErrorCode.PRODUCT_REQUIRES_ACTIVE_VARIANT;
            case PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT ->
                    CatalogErrorCode.PRODUCT_CANNOT_ASSIGN_CATEGORY_ROOT;

            // VARIANT
            case INVALID_VARIANT_NAME ->
                    CatalogErrorCode.INVALID_VARIANT_NAME;

            case INVALID_VARIANT_PRICE ->
                    CatalogErrorCode.INVALID_VARIANT_PRICE;

            case INVALID_SKU ->
                    CatalogErrorCode.INVALID_SKU;

            case INVALID_VARIANT_ORDER ->
                    CatalogErrorCode.INVALID_VARIANT_ORDER;

            case LAST_ACTIVE_VARIANT_CANNOT_BE_DEACTIVATED ->
                    CatalogErrorCode.LAST_ACTIVE_VARIANT_CANNOT_BE_DEACTIVATED;

            case VARIANT_NOT_IN_PRODUCT ->
                    CatalogErrorCode.PRODUCT_VARIANT_NOT_FOUND;


            //VARIANT IMAGE
            case INVALID_VARIANT_IMAGE_URL ->
                    CatalogErrorCode.INVALID_VARIANT_IMAGE_URL;

            case INVALID_VARIANT_IMAGE_PUBLIC_ID ->
                    CatalogErrorCode.INVALID_VARIANT_IMAGE_PUBLIC_ID;

            case INVALID_VARIANT_IMAGE_SORT_ORDER ->
                    CatalogErrorCode.INVALID_VARIANT_IMAGE_SORT_ORDER;

            case VARIANT_IMAGE_REQUIRES_VARIANT ->
                    CatalogErrorCode.VARIANT_IMAGE_REQUIRES_VARIANT;

            case VARIANT_IMAGE_LIMIT_EXCEEDED ->
                    CatalogErrorCode.VARIANT_IMAGE_LIMIT_EXCEEDED;

            case VARIANT_IMAGE_NOT_IN_VARIANT ->
                    CatalogErrorCode.VARIANT_IMAGE_NOT_FOUND;

            case INVALID_VARIANT_IMAGE_ORDER ->
                    CatalogErrorCode.INVALID_VARIANT_IMAGE_ORDER;
        };
    }
}
