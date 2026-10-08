package com.insideinvoice.config;

import com.insideinvoice.auth.entity.Role;
import com.insideinvoice.auth.entity.User;
import com.insideinvoice.auth.repository.UserRepository;
import com.insideinvoice.business.entity.Business;
import com.insideinvoice.business.repository.BusinessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Component
// Seeding the default admin used to run on every profile (the @Profile("local") guard
// was removed). Combined with the hard-coded password this meant any fresh deployment
// booted with a known ADMIN credential — platform takeover on first login. Seeding is
// now opt-in via property; existing accounts are never touched.
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;

    /** Off by default; enable explicitly (env APP_SEED_DEFAULT_ADMIN=true) for throwaway local/dev DBs. */
    @Value("${app.seed-default-admin:false}")
    private boolean seedDefaultAdmin;

    public DataSeeder(UserRepository userRepository, BusinessRepository businessRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        String adminUsername = "invoiceinside";
        String adminPassword = "insideinvoice";

        if (!seedDefaultAdmin) {
            log.debug("Default admin seeding disabled (app.seed-default-admin=false)");
            return;
        }

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
                    .businessId(adminBusiness.getId())
                    .businessSetupCompleted(true)
                    .build();
            userRepository.save(admin);

            // Never log the password itself.
            log.info("Default admin user created — username: {}", adminUsername);
        } else {
            log.info("Admin user already exists — skipping creation: {}", adminUsername);
        }
    }
}
