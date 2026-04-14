package dev.phatanon;

import dev.phatanon.service.TwitchBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

/**
 * A startup component that performs connectivity checks for database and Twitch services.
 * Implements {@link CommandLineRunner} to run immediately after application context is fully started.
 */
@Component
public class ConnectionStartupLogger implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionStartupLogger.class);

    /**
     * Interface for Twitch bot status verification.
     */
    public interface ITwitchBotService {
        /**
         * Checks if the Twitch IRC connection is active.
         * @return true if connected, false otherwise
         */
        boolean isTwitchConnected();
    }

    private final JdbcOperations jdbcTemplate;
    private final ITwitchBotService twitchBotService;

    @Value("${spring.application.build-id:unknown}")
    private String buildId;

    public ConnectionStartupLogger(JdbcOperations jdbcTemplate, ITwitchBotService twitchBotService) {
        this.jdbcTemplate = jdbcTemplate;
        this.twitchBotService = twitchBotService;
    }

    @Override
    public void run(String... args) {
        logger.info("Application Build ID: {}", buildId);
        logger.info("Starting connection acquisition checks...");

        checkDatabaseConnection();
        checkTwitchConnection();

        logger.info("Startup connection checks completed.");
    }

    private void checkTwitchConnection() {
        try {
            logger.info("Verifying Twitch connection status...");
            if (twitchBotService.isTwitchConnected()) {
                logger.info("✅ Successfully connected to Twitch IRC.");
            } else {
                logger.warn("⚠️ Twitch IRC not yet connected (it may still be connecting in the background).");
            }
        } catch (Exception e) {
            logger.error("❌ Error checking Twitch connection: {}", e.getMessage());
        }
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
