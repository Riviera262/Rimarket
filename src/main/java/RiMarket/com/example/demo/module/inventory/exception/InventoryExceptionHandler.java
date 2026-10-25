package RiMarket.com.example.demo.module.inventory.exception;

import RiMarket.com.example.demo.module.inventory.domain.exception.InventoryDomainException;
import RiMarket.com.example.demo.shared.exception.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = {"RiMarket.com.example.demo.module.inventory",
        "RiMarket.com.example.demo.application.variant"})
public class InventoryExceptionHandler {
    @ExceptionHandler(InventoryDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(InventoryDomainException exception) {
        InventoryErrorCode error = switch (exception.getViolation()) {
            case INVALID_QUANTITY -> InventoryErrorCode.INVALID_QUANTITY;
            case STOCK_OUT_OF_RANGE -> InventoryErrorCode.STOCK_OUT_OF_RANGE;
            case INVALID_METADATA -> InventoryErrorCode.INVALID_METADATA;
            case INVALID_ACTOR -> InventoryErrorCode.INVALID_ACTOR;
            case INVALID_OPERATION -> InventoryErrorCode.INVALID_OPERATION;
            case INVALID_REFERENCE -> InventoryErrorCode.INVALID_REFERENCE;
        };
        ErrorResponse response = new ErrorResponse(error.getStatus().value(), error.getCode(), error.getMessage());
        return ResponseEntity.status(error.getStatus()).body(response);
    }
}
