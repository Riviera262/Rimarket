package RiMarket.com.example.demo.module.inventory.exception;

import RiMarket.com.example.demo.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements ErrorCode {
    ITEM_NOT_FOUND("INVENTORY_001", "Inventory item not found", HttpStatus.NOT_FOUND),
    ITEM_ALREADY_EXISTS("INVENTORY_002", "Inventory item already exists", HttpStatus.CONFLICT),
    CATALOG_VARIANT_NOT_FOUND("INVENTORY_003", "Catalog variant not found", HttpStatus.NOT_FOUND),
    INVALID_QUANTITY("INVENTORY_004", "Receive quantity must be positive; adjustment delta must be nonzero", HttpStatus.BAD_REQUEST),
    STOCK_OUT_OF_RANGE("INVENTORY_005", "Physical stock cannot be below reserved stock or exceed the supported range", HttpStatus.CONFLICT),
    INVALID_METADATA("INVENTORY_006", "Inventory metadata is invalid or adjustment reason is missing", HttpStatus.BAD_REQUEST),
    INVALID_ACTOR("INVENTORY_007", "A valid inventory actor is required", HttpStatus.BAD_REQUEST),
    INVALID_OPERATION("INVENTORY_008", "An operation ID is required", HttpStatus.BAD_REQUEST),
    INVALID_REFERENCE("INVENTORY_009", "Reference type and ID must be provided together", HttpStatus.BAD_REQUEST),
    OPERATION_CONFLICT("INVENTORY_010", "Operation ID was already used for a different command or actor", HttpStatus.CONFLICT),
    INVALID_PAGE("INVENTORY_011", "Page must be nonnegative and size must be between 1 and 100", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;
}
