package RiMarket.com.example.demo.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.nio.file.AccessDeniedException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

   //Custom Enum exceptions
   @ExceptionHandler(AppException.class)
   public ResponseEntity<ErrorResponse> handle(AppException ex){
      ErrorCode error = ex.getErrorCode();
      ErrorResponse response =
              new ErrorResponse(
                      error.getStatus().value(),
                      error.getCode(),
                      error.getMessage()
              );
      return ResponseEntity.status(error.getStatus()).body(response);
   }

   //Invalid Email or Password(401)
   @ExceptionHandler(BadCredentialsException.class)
   public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex){
      ErrorResponse response = new ErrorResponse(
              HttpStatus.UNAUTHORIZED.value(),
              "LOGIN-FAILED",
              "Invalid Email or Password"
      );
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
   }

   //Validation format inputs(400)
   @ExceptionHandler(MethodArgumentNotValidException.class)
   public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex){
      Map<String, String> errors = new HashMap<>();
      for (FieldError error : ex.getBindingResult().getFieldErrors()){
         errors.put(error.getField(), error.getDefaultMessage());
      }


      ErrorResponse response = new ErrorResponse(
              HttpStatus.BAD_REQUEST.value(),
              "INVALID-INPUT",
              "Validation failed. Please check your inputs",
              errors
      );
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
   }

   //Validate format request(400)
   @ExceptionHandler(MethodArgumentTypeMismatchException.class)
   public ResponseEntity<ErrorResponse> handleTypeMismatch(
           MethodArgumentTypeMismatchException ex) {

      ErrorResponse response = new ErrorResponse(
              HttpStatus.BAD_REQUEST.value(),
              "INVALID-REQUEST",
              "Invalid request parameter."
      );

      return ResponseEntity
              .status(HttpStatus.BAD_REQUEST)
              .body(response);
   }

   //Account not yet verified(403)
   @ExceptionHandler(DisabledException.class)
   public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex){
      ErrorResponse response = new ErrorResponse(
              HttpStatus.FORBIDDEN.value(),
              "ACCOUNT-IS-NOT-VERIFIED",
              "Account is not verified. Please check your email."
      );
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
   }

   //Account is locked(403)
   @ExceptionHandler(LockedException.class)
   public ResponseEntity<ErrorResponse> handleLocked(LockedException ex){
      ErrorResponse response = new ErrorResponse(
              HttpStatus.FORBIDDEN.value(),
              "ACCOUNT-IS-LOCKED",
              "Account is locked. Please contact support."
      );
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
   }

   //Account is deleted/expired(403)
   @ExceptionHandler(AccountExpiredException.class)
   public ResponseEntity<ErrorResponse> handleExpired(AccountExpiredException ex){
      ErrorResponse response = new ErrorResponse(
              HttpStatus.FORBIDDEN.value(),
              "ACCOUNT-NOT-FOUND",
              "Account no longer exists."
      );
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
   }

   //Access denied(403)
   @ExceptionHandler(AccessDeniedException.class)
   public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex){
      ErrorResponse response = new ErrorResponse(
              HttpStatus.FORBIDDEN.value(),
              "ACCESS-DENIED",
              "You do not have permission to access this resource."
      );
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
   }

   //Conflict error(409)
   @ExceptionHandler(DataIntegrityViolationException.class)
   public ResponseEntity<ErrorResponse> handleDatabaseConflict(Exception ex) {
      // Return a clean 409 Conflict error to the frontend instead of a 500 crash
      ErrorResponse response = new ErrorResponse( HttpStatus.CONFLICT.value(),
              "DATA-CONFLICT-OCCURRED",
              "A data conflict occurred (e.g., Duplicate SKU or Slug)."
      );
      return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
   }

   @ExceptionHandler(ConcurrencyFailureException.class)
   public ResponseEntity<ErrorResponse> handleOptimisticLockConflict(
           ConcurrencyFailureException ex
   ) {
      CommonErrorCode error = CommonErrorCode.CONCURRENT_MODIFICATION;
      ErrorResponse response = new ErrorResponse(
              error.getStatus().value(),
              error.getCode(),
              error.getMessage()
      );

      return ResponseEntity.status(error.getStatus()).body(response);
   }

   //Unexpected error(500)
   @ExceptionHandler(InternalAuthenticationServiceException.class)
   public ResponseEntity<ErrorResponse> handleInternalAuth(InternalAuthenticationServiceException ex){
      Throwable cause = ex.getCause();

      if (cause instanceof DisabledException)
         return handleDisabled((DisabledException) cause);

      if (cause instanceof LockedException)
         return handleLocked((LockedException) cause);

      log.error("Internal auth error: ", ex);
      ErrorResponse response = new ErrorResponse(
              HttpStatus.INTERNAL_SERVER_ERROR.value(),
              "UNEXPECTED-ERROR-OCCURRED",
              "An unexpected error occurred. Please try again later."
      );
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
   }

   //Logging fallback(500)
   @ExceptionHandler(Exception.class)
   public ResponseEntity<ErrorResponse> handleAllOtherException(Exception ex){
      log.error("CRITICAL SERVER ERROR: " + ex);
      ErrorResponse response = new ErrorResponse(
              HttpStatus.INTERNAL_SERVER_ERROR.value(),
              "UNEXPECTED-ERROR-OCCURRED",
              "An unexpected error occurred. Please try again later."
      );
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
   }

}
