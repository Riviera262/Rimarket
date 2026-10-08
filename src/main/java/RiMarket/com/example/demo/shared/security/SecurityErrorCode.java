package RiMarket.com.example.demo.shared.security;

import RiMarket.com.example.demo.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SecurityErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(
            "SECURITY_001",
            "Invalid email or password",
            HttpStatus.UNAUTHORIZED
    ),

    INVALID_ACCESS_TOKEN(
            "SECURITY_002",
            "Access token is invalid or expired",
            HttpStatus.UNAUTHORIZED
    ),

    AUTHENTICATION_REQUIRED(
            "SECURITY_003",
            "Authentication is required",
            HttpStatus.UNAUTHORIZED
    ),

    ACCESS_DENIED(
            "SECURITY_004",
            "You do not have permission to access this resource",
            HttpStatus.FORBIDDEN
    ),

    ACCOUNT_NOT_VERIFIED(
            "SECURITY_005",
            "Account is not verified. Please check your email",
            HttpStatus.FORBIDDEN
    ),

    ACCOUNT_LOCKED(
            "SECURITY_006",
            "Account is locked. Please contact support",
            HttpStatus.FORBIDDEN
    ),

    ACCOUNT_EXPIRED(
            "SECURITY_007",
            "Account no longer exists",
            HttpStatus.FORBIDDEN
    );

    private final String code;
    private final String message;
    private final HttpStatus status;
}