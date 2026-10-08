package RiMarket.com.example.demo.module.identity.dto;

public record AuthResponse (
        String accessToken,
        String tokenType
    ){
        public AuthResponse(String accessToken){
            this(accessToken, "Bearer");
        }
}
