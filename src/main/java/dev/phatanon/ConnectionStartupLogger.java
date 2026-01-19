package dev.phatanon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

@Component
public class ConnectionStartupLogger implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionStartupLogger.class);

    private final JdbcOperations jdbcTemplate;
    private final RedisConnectionFactory redisConnectionFactory;

    public ConnectionStartupLogger(JdbcOperations jdbcTemplate, RedisConnectionFactory redisConnectionFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public void run(String... args) {
        logger.info("Starting connection acquisition checks...");

        checkDatabaseConnection();
        checkRedisConnection();

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

    private void checkRedisConnection() {
        try {
            logger.info("Attempting to acquire Redis connection...");
            RedisConnection connection = redisConnectionFactory.getConnection();
            String ping = connection.ping();
            if ("PONG".equalsIgnoreCase(ping)) {
                logger.info("✅ Successfully connected to Redis.");
            } else {
                logger.error("❌ Failed to verify Redis connection (unexpected ping response: {}).", ping);
            }
            connection.close();
        } catch (Exception e) {
            logger.error("❌ Error acquiring Redis connection: {}", e.getMessage());
        }
    }
}
