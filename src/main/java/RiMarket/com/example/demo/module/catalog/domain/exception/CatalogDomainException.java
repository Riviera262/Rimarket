package RiMarket.com.example.demo.module.catalog.domain.exception;

import lombok.Getter;

@Getter
public class CatalogDomainException extends RuntimeException {

    private final CatalogDomainViolation violation;

    public CatalogDomainException(
            CatalogDomainViolation violation
    ) {
        super(violation.name());
        this.violation = violation;
    }
}