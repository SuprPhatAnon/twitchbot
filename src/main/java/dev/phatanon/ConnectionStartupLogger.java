package dev.phatanon;

import dev.phatanon.entity.Role;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.service.SongService;
import dev.phatanon.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

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
         * Checks if the Twitch EventSub connection is active.
         * @return true if connected, false otherwise
         */
        boolean isTwitchConnected();
    }

    private final JdbcOperations jdbcTemplate;
    private final ITwitchBotService twitchBotService;
    private final SongRepository songRepository;
    private final SongService songService;
    private final UserService userService;

    @Value("${spring.application.build-id:unknown}")
    private String buildId;

    public ConnectionStartupLogger(JdbcOperations jdbcTemplate, ITwitchBotService twitchBotService, SongRepository songRepository, SongService songService, UserService userService) {
        this.jdbcTemplate = jdbcTemplate;
        this.twitchBotService = twitchBotService;
        this.songRepository = songRepository;
        this.songService = songService;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        logger.info("Application Build ID: {}", buildId);
        logger.info("Starting connection acquisition checks...");

        checkDatabaseConnection();
        checkTwitchConnection();
        backfillCoverArt();
        initializeAdminUser();

        logger.info("Startup connection checks completed.");
    }

    private void initializeAdminUser() {
        if (!userService.existsByUsername("admin")) {
            logger.info("Initializing default admin user...");
            userService.createUser("admin", "admin", Set.of(Role.ROLE_ADMIN));
            logger.info("✅ Default admin user created.");
        }
    }

    private void backfillCoverArt() {
        try {
            logger.info("Checking for songs without cover art to backfill...");
            List<dev.phatanon.entity.Song> songs = songRepository.findAll();
            int backfilledCount = 0;
            for (dev.phatanon.entity.Song song : songs) {
                if (song.getCoverArt() == null) {
                    songService.updateMetadata(song);
                    if (song.getCoverArt() != null) {
                        songRepository.save(song);
                        backfilledCount++;
                    }
                }
            }
            if (backfilledCount > 0) {
                logger.info("✅ Backfilled cover art for {} songs.", backfilledCount);
            } else {
                logger.info("No cover art backfill needed.");
            }
        } catch (Exception e) {
            logger.error("❌ Error backfilling cover art: {}", e.getMessage());
        }
    }

    private void checkTwitchConnection() {
        try {
            logger.info("Verifying Twitch connection status...");
            if (twitchBotService.isTwitchConnected()) {
                logger.info("✅ Successfully connected to Twitch EventSub.");
            } else {
                logger.warn("⚠️ Twitch EventSub not yet connected (it may still be connecting in the background).");
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
