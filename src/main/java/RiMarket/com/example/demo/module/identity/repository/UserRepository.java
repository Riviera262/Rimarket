package RiMarket.com.example.demo.module.identity.repository;

import RiMarket.com.example.demo.module.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
;
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
