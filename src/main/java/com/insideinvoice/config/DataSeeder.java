package com.insideinvoice.config;

import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        User existingAdmin = userRepository.findByEmail("mohammedjawadsaleem17@gmail.com").orElse(null);
        if (existingAdmin != null) {
            boolean updated = false;
            if (existingAdmin.getRawPassword() == null) {
                existingAdmin.setRawPassword("saleem");
                updated = true;
            }
            if (existingAdmin.getUsername() == null || existingAdmin.getUsername().equals(existingAdmin.getEmail())) {
                existingAdmin.setUsername("admin");
                updated = true;
            }
            if (updated) {
                userRepository.save(existingAdmin);
                log.info("Updated existing admin: rawPassword + username");
            }
            log.info("Admin user already exists, skipping full seed");
            return;
        }

        Business adminBusiness = Business.builder()
                .businessName("Inside Invoice Admin")
                .ownerName("Admin")
                .invoicePrefix("ADMIN")
                .nextInvoiceSequence(1L)
                .build();
        adminBusiness = businessRepository.save(adminBusiness);

        User admin = User.builder()
                .name("Admin")
                .username("admin")
                .email("mohammedjawadsaleem17@gmail.com")
                .password(passwordEncoder.encode("saleem"))
                .rawPassword("saleem")
                .role(Role.ADMIN)
                .businessId(adminBusiness.getId())
                .businessSetupCompleted(true)
                .build();
        userRepository.save(admin);

        log.info("Admin user seeded: mohammedjawadsaleem17@gmail.com / admin");
    }
}
