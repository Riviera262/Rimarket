package RiMarket.com.example.demo.module.identity.repository;

import RiMarket.com.example.demo.module.identity.entity.redis.RefreshToken;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, String> {

    Optional<RefreshToken> findById(String refreshToken);
    void deleteUserId(UUID userId);
}
