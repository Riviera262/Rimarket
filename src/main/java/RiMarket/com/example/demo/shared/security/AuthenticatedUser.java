package RiMarket.com.example.demo.shared.security;

import java.security.Principal;
import java.util.UUID;

/** Request identity built only from a verified JWT, without loading a User entity. */
public record AuthenticatedUser(UUID userId, String email) implements Principal {
    public AuthenticatedUser {
        if (userId == null || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Verified token must contain a user ID and subject");
        }
    }

    @Override
    public String getName() {
        return email;
    }
}
