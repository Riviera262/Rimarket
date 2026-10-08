package RiMarket.com.example.demo.module.identity.controller;

import RiMarket.com.example.demo.module.identity.dto.AuthResponse;
import RiMarket.com.example.demo.module.identity.dto.LoginRequest;
import RiMarket.com.example.demo.module.identity.dto.RegisterRequest;
import RiMarket.com.example.demo.module.identity.dto.TokenPair;
import RiMarket.com.example.demo.module.identity.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${application.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${application.security.cookie.secure}")
    private boolean secureCookie;

    @PostMapping("/register")
    public ResponseEntity<String> register (@Valid @RequestBody RegisterRequest request){
        authService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body("Registration successful. Please check email to verify your account");
    }

    @GetMapping("/verify")
    public ResponseEntity<String> verify (@RequestParam("token") String token){
        authService.verifyAccount(token);
        return ResponseEntity.ok("Account successfully verified. You may log in.");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login (@Valid @RequestBody LoginRequest request){

        TokenPair tokens = authService.login(request);

        //Create Refresh Token Cookie (7 days)
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.refreshToken())
                .httpOnly(true)
                .secure(secureCookie) //Set to true when deploy to production
                .path("/api/auth/refresh")
                .maxAge(refreshTokenExpiration)
                .sameSite("Strict") //CRSF protection
                .build();

        //Save AT in body, RT in cookie
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new AuthResponse(tokens.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue("refreshToken") String refreshToken){
        //Validate and generate new AT
        String newAccessToken= authService.refreshToken(refreshToken);

        //If done, do nothing
        return ResponseEntity.ok(new AuthResponse(newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@CookieValue(value = "refreshToken", required = true) String refreshToken){

        //Check and delete RT in redis
        authService.logout(refreshToken);

        //Build new RT with 0 time alive
        ResponseCookie deletedRefreshToken = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/auth/refresh")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deletedRefreshToken.toString())
                .body("Logout successfully");
    }
}
