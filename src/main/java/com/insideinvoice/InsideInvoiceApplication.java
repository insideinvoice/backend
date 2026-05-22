package com.insideinvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class InsideInvoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsideInvoiceApplication.class, args);
    }
}
