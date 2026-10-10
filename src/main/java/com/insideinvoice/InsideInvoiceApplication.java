package com.insideinvoice;

import com.insideinvoice.config.LogbackClassloadingGuard;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class InsideInvoiceApplication {

    public static void main(String[] args) {
        LogbackClassloadingGuard.preload();
        SpringApplication.run(InsideInvoiceApplication.class, args);
    }
}
