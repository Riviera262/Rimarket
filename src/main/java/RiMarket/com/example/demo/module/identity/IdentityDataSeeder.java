package RiMarket.com.example.demo.module.identity;

import RiMarket.com.example.demo.module.identity.entity.Role;
import RiMarket.com.example.demo.module.identity.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class IdentityDataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // This code runs automatically exactly once when Spring Boot starts up.

        if (roleRepository.count() == 0) {
            System.out.println("Seeding initial roles into the database...");

            Role customerRole = new Role();
            customerRole.setName("ROLE_CUSTOMER");

            Role staffRole = new Role();
            staffRole.setName("ROLE_STAFF");

            Role adminRole = new Role();
            adminRole.setName("ROLE_ADMIN");

            roleRepository.save(customerRole);
            roleRepository.save(adminRole);

            System.out.println("✅ Roles seeded successfully!");
        } else {
            System.out.println("👍 Roles already exist. Skipping seed.");
        }
    }
}