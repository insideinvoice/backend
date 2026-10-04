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
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Component
// Removed @Profile("local") so it runs on any profile
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, BusinessRepository businessRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Create the admin user on the fly if not exists
        // Username: invoiceinside, password: insideinvoice
        String adminUsername = "invoiceinside";
        String adminPassword = "insideinvoice";

        if (!userRepository.existsByUsername(adminUsername)) {
            Business adminBusiness = Business.builder()
                    .businessName("Inside Invoice Admin")
                    .ownerName("Admin")
                    .invoicePrefix("ADMIN")
                    .nextInvoiceSequence(1L)
                    .build();
            adminBusiness = businessRepository.save(adminBusiness);

            User admin = User.builder()
                    .name("Admin")
                    .username(adminUsername)
                    .email(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .rawPassword(adminPassword)
                    .role(Role.ADMIN)
                    .businessId(null)
                    .businessSetupCompleted(true)
                    .build();
            userRepository.save(admin);

            log.info("Default admin user created — username: {}, password: {}", adminUsername, adminPassword);
        } else {
            log.info("Admin user already exists — skipping creation: {}", adminUsername);
        }
    }
}
