package dev.phatanon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

@Component
public class ConnectionStartupLogger implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionStartupLogger.class);

    private final JdbcOperations jdbcTemplate;

    public ConnectionStartupLogger(JdbcOperations jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        logger.info("Starting connection acquisition checks...");

        checkDatabaseConnection();

        logger.info("Startup connection checks completed.");
    }

    private void checkDatabaseConnection() {
        try {
            logger.info("Attempting to acquire MariaDB connection...");
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                logger.info("✅ Successfully connected to MariaDB.");
            } else {
                logger.error("❌ Failed to verify MariaDB connection (unexpected result).");
            }
        } catch (Exception e) {
            logger.error("❌ Error acquiring MariaDB connection: {}", e.getMessage());
        }
    }
}
