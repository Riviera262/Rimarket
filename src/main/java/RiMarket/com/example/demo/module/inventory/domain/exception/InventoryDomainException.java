package RiMarket.com.example.demo.module.inventory.domain.exception;

import lombok.Getter;

@Getter
public class InventoryDomainException extends RuntimeException {
    private final InventoryDomainViolation violation;

    public InventoryDomainException(InventoryDomainViolation violation) {
        super(violation.name());
        this.violation = violation;
    }
}
