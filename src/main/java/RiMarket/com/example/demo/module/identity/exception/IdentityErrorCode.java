package RiMarket.com.example.demo.module.identity.exception;

import RiMarket.com.example.demo.shared.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum IdentityErrorCode implements ErrorCode {

    // =========================================================
    // USER
    // =========================================================
    USER_NOT_FOUND(
            "IDENTITY_001",
            "User not found",
            HttpStatus.NOT_FOUND
    ),

    USER_EMAIL_ALREADY_EXISTS(
            "IDENTITY_002",
            "Email already exists",
            HttpStatus.CONFLICT
    ),

    USER_ALREADY_VERIFIED(
            "IDENTITY_003",
            "User is already verified",
            HttpStatus.CONFLICT
    ),

    USER_NOT_VERIFIED(
            "IDENTITY_004",
            "User has not been verified",
            HttpStatus.FORBIDDEN
    ),

    USER_SUSPENDED(
            "IDENTITY_005",
            "User account is suspended",
            HttpStatus.FORBIDDEN
    ),


    // =========================================================
    // ROLE
    // =========================================================
    ROLE_NOT_FOUND(
            "IDENTITY_101",
            "Required role was not found",
            HttpStatus.INTERNAL_SERVER_ERROR
    ),


    // =========================================================
    // EMAIL VERIFICATION
    // =========================================================
    INVALID_VERIFICATION_TOKEN(
            "IDENTITY_201",
            "Verification token is invalid",
            HttpStatus.BAD_REQUEST
    ),

    VERIFICATION_TOKEN_EXPIRED(
            "IDENTITY_202",
            "Verification token has expired",
            HttpStatus.BAD_REQUEST
    ),


    // =========================================================
    // REFRESH TOKEN
    // =========================================================
    INVALID_REFRESH_TOKEN(
            "IDENTITY_301",
            "Refresh token is invalid or expired",
            HttpStatus.UNAUTHORIZED
    ),


    // =========================================================
    // PASSWORD RESET
    // =========================================================
    INVALID_PASSWORD_RESET_TOKEN(
            "IDENTITY_401",
            "Password reset token is invalid or expired",
            HttpStatus.BAD_REQUEST
    );

    private final String code;
    private final String message;
    private final HttpStatus status;

}