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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        User existingAdmin = userRepository.findByEmail("invoiceinside").orElse(null);
        if (existingAdmin != null) {
            log.info("Admin user already exists, skipping seed");
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
                .username("invoiceinside")
                .email("invoiceinside")
                .password(passwordEncoder.encode("insideinvoice"))
                .rawPassword("insideinvoice")
                .role(Role.ADMIN)
                .businessId(adminBusiness.getId())
                .businessSetupCompleted(true)
                .build();
        userRepository.save(admin);

        log.info("Default admin created — email/username: invoiceinside, password: insideinvoice");
    }
}
