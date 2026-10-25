package RiMarket.com.example.demo.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // 1. Extract token from Cookies
        String jwt = extractTokenFromRequest(request);

        // 2. If no token, let it pass to the next filter (Spring Security will block it later if it's a protected route)
        if (jwt == null){
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Authenticate only from verified token claims; preserve an existing authentication.
            if (SecurityContextHolder.getContext().getAuthentication() == null){
                if (jwtService.isTokenValid(jwt)){
                    // Older tokens without userId cannot supply a trustworthy audit actor.
                    AuthenticatedUser principal = new AuthenticatedUser(
                            jwtService.extractUserId(jwt), jwtService.extractEmail(jwt));
                    //Extract roles from the Jwt Payload
                    List<String> roles = jwtService.extractRoles(jwt);
                    var authorities = roles.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    //Create Spring Security without hitting the database
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (Exception e) {
            // Token is invalid, expired, or tampered with. Do nothing, context stays empty.
            // Protected routes return 401 through the configured AuthenticationEntryPoint.
        }

        filterChain.doFilter(request, response);

    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        // First, check cookies
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // Fallback for Postman testing (Bearer Header)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}
