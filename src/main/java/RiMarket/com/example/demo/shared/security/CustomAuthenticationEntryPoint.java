package RiMarket.com.example.demo.shared.security;

import RiMarket.com.example.demo.shared.exception.ErrorCode;
import RiMarket.com.example.demo.shared.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        // Match your custom enterprise error structure
        ErrorCode errorCode = SecurityErrorCode.INVALID_ACCESS_TOKEN; // Or create a generic INVALID_ACCESS_TOKEN (401)

        ErrorResponse errorResponse = new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage()
        );

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(errorCode.getStatus().value());

        // Serialize the record object to JSON using Jackson ObjectMapper
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Ensures LocalDateTime serializes beautifully
        response.getWriter().write(mapper.writeValueAsString(errorResponse));
    }
}