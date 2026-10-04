package com.insideinvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class, org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class})
@EnableJpaAuditing
@EnableScheduling
public class InsideInvoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsideInvoiceApplication.class, args);
    }
}
