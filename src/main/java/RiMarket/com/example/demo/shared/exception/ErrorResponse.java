package RiMarket.com.example.demo.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> details,
        LocalDateTime timeStamp
){
    public ErrorResponse(int status, String error, String message){
        this(status, error, message, null ,LocalDateTime.now());
    }

    public ErrorResponse(int status, String error, String message, Map<String, String> details){
        this(status, error, message, details, LocalDateTime.now());
    }
}