package RiMarket.com.example.demo.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    FILE_REQUIRED(
            "COMMON_001",
            "File is required",
            HttpStatus.BAD_REQUEST
    ),

    FILE_TOO_LARGE(
            "COMMON_002",
            "File exceeds the maximum allowed size",
            HttpStatus.CONTENT_TOO_LARGE
    ),

    UNSUPPORTED_FILE_TYPE(
            "COMMON_003",
            "File type is not supported",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE
    ),

    FILE_UPLOAD_FAILED(
            "COMMON_004",
            "Failed to upload file",
            HttpStatus.BAD_GATEWAY
    ),

    FILE_DELETE_FAILED(
            "COMMON_005",
            "Failed to delete file",
            HttpStatus.BAD_GATEWAY
    ),

    CONCURRENT_MODIFICATION(
            "COMMON_006",
            "The resource was changed by another request. Please retry with fresh data.",
            HttpStatus.CONFLICT
    );

    private final String code;
    private final String message;
    private final HttpStatus status;
}
