package RiMarket.com.example.demo.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    // Generate Access token with role inside it
    public String generateAccessToken(SecurityUser user) {
        Map<String, Object> claims = new HashMap<>();

        // Put role inside Token
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        claims.put("roles", roles);
        claims.put("userId", user.getUserId().toString());

        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername()) // In our case, username is the email
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration * 1000L))
                .signWith(getSignInKey())
                .compact();
    }

    // Extract Username (Email) from the token
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        String id = extractAllClaims(token).get("userId", String.class);
        return id == null ? null : UUID.fromString(id);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token){
        return extractAllClaims(token).get("roles", List.class);
    }

    // Validate Token
    public boolean isTokenValid(String token) {
        try {
            return extractAllClaims(token).getExpiration().after(new Date());
        } catch (Exception e){
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Decode the secret key
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}