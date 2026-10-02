package com.insideinvoice.backup.util;

import java.net.URI;
import java.net.URISyntaxException;

public final class DatabaseConnectionParser {

    private DatabaseConnectionParser() {
    }

    public static ParsedDatabase parse(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC URL must not be null or blank");
        }

        try {
            URI uri = new URI(jdbcUrl.replaceFirst("^jdbc:postgresql:", "http:"));

            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Could not extract host from JDBC URL");
            }

            int port = uri.getPort();
            if (port == -1) {
                port = 5432;
            }

            String database = uri.getPath();
            if (database == null || database.isBlank()) {
                throw new IllegalArgumentException("Could not extract database name from JDBC URL");
            }
            database = database.startsWith("/") ? database.substring(1) : database;

            return new ParsedDatabase(host, port, database);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid JDBC URL format: " + e.getMessage(), e);
        }
    }

    public record ParsedDatabase(String host, int port, String database) {
    }
}
