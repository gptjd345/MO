package com.todo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            throw new RuntimeException("DATABASE_URL environment variable is not set");
        }

        System.out.println("[DataSource] Parsing DATABASE_URL...");

        try {
            URI uri = new URI(databaseUrl);
            String userInfo = uri.getUserInfo();
            String username = userInfo.split(":")[0];
            String password = userInfo.split(":")[1];

            int port = uri.getPort();
            String host = uri.getHost();
            String path = uri.getPath();
            String query = uri.getQuery();

            String jdbcUrl = "jdbc:postgresql://" + host;
            if (port > 0) {
                jdbcUrl += ":" + port;
            }
            jdbcUrl += path;
            if (query != null && !query.isEmpty()) {
                jdbcUrl += "?" + query;
            }

            System.out.println("[DataSource] JDBC URL: " + jdbcUrl.replaceAll("password=[^&]*", "password=***"));
            System.out.println("[DataSource] Host: " + host + ", Port: " + port);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
            config.setConnectionTimeout(10000);
            config.setValidationTimeout(5000);
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);
            config.setInitializationFailTimeout(30000);

            System.out.println("[DataSource] Creating HikariDataSource with timeouts...");
            HikariDataSource ds = new HikariDataSource(config);
            System.out.println("[DataSource] DataSource created successfully");
            return ds;
        } catch (Exception e) {
            System.err.println("[DataSource] FAILED to create DataSource: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to parse DATABASE_URL: " + e.getMessage(), e);
        }
    }
}
