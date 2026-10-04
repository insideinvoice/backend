package com.insideinvoice.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minIdle;

    @Value("${spring.datasource.hikari.idle-timeout:300000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1200000}")
    private long maxLifetime;

    @Bean
    @Primary
    public DataSource dataSource() {
        // Extract password from URL and strip first 6 characters (obfuscation prefix)
        String url = datasourceUrl;
        String actualUrl = url;
        
        // Pattern: jdbc:postgresql://user:password@host:port/db
        // Extract password and strip first 6 chars (obfuscation prefix)
        int userStart = url.indexOf("://") + 3;
        int passwordStart = url.indexOf(":", userStart);
        int passwordEnd = url.indexOf("@", passwordStart);
        
        if (passwordStart > 0 && passwordEnd > passwordStart) {
            String obfuscatedPassword = url.substring(passwordStart + 1, passwordEnd);
            if (obfuscatedPassword.length() > 6) {
                String actualPassword = obfuscatedPassword.substring(6);
                actualUrl = url.substring(0, passwordStart + 1) + actualPassword + url.substring(passwordEnd);
            }
        }

        DataSourceProperties props = new DataSourceProperties();
        props.setUrl(actualUrl);
        props.setUsername(username);
        props.setDriverClassName("org.postgresql.Driver");

        HikariDataSource ds = props.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(5);
        ds.setIdleTimeout(300000);
        ds.setConnectionTimeout(20000);
        ds.setMaxLifetime(1200000);
        return ds;
    }
}
