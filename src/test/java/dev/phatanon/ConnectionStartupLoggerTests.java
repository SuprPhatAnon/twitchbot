package dev.phatanon;

import dev.phatanon.repository.SongRepository;
import dev.phatanon.service.SongService;
import dev.phatanon.service.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
class ConnectionStartupLoggerTests {

    @Nested
    @SpringBootTest(properties = "spring.application.build-id=test-build-id")
    @ExtendWith(OutputCaptureExtension.class)
    class SuccessTests {
        @Test
        void testConnectionLogging(CapturedOutput output) {
            assertThat(output.getOut()).contains("Application Build ID: test-build-id");
            assertThat(output.getOut()).contains("Starting connection acquisition checks...");
            assertThat(output.getOut()).contains("✅ Successfully connected to MariaDB.");
            assertThat(output.getOut()).contains("Verifying Twitch connection status...");
            assertThat(output.getOut()).contains("Startup connection checks completed.");
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class FailureTests {
        @Test
        void testMariaDBConnectionFailure(CapturedOutput output) {
            // Arrange
            JdbcOperations jdbcTemplate = mock(JdbcOperations.class);
            // Using a mock to handle multiple methods in the interface
            ConnectionStartupLogger.ITwitchBotService twitchBotService = mock(ConnectionStartupLogger.ITwitchBotService.class);
            when(twitchBotService.isStreamerConnected()).thenReturn(false);
            when(twitchBotService.isBotConnected()).thenReturn(false);
            
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                    .thenThrow(new RuntimeException("Connection refused"));
            
            // Act
            SongRepository songRepository = mock(SongRepository.class);
            SongService songService = mock(SongService.class);
            UserService userService = mock(UserService.class);
            ConnectionStartupLogger logger = new ConnectionStartupLogger(jdbcTemplate, twitchBotService, songRepository, songService, userService);
            ReflectionTestUtils.setField(logger, "buildId", "test-build-id");
            logger.run();

            // Assert
            assertThat(output.getOut()).contains("Application Build ID: test-build-id");
            assertThat(output.getOut()).contains("❌ Error acquiring MariaDB connection: Connection refused");
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class BackfillTests {
        @Test
        void testBackfillMetadataAtStartup(CapturedOutput output) {
            // Arrange
            JdbcOperations jdbcTemplate = mock(JdbcOperations.class);
            ConnectionStartupLogger.ITwitchBotService twitchBotService = mock(ConnectionStartupLogger.ITwitchBotService.class);
            SongRepository songRepository = mock(SongRepository.class);
            SongService songService = mock(SongService.class);
            UserService userService = mock(UserService.class);

            dev.phatanon.entity.Song songWithMissingDuration = new dev.phatanon.entity.Song("Title", "Artist", "url");
            songWithMissingDuration.setDurationSeconds(null);
            songWithMissingDuration.setCoverArt("existing-art");

            dev.phatanon.entity.Song songWithMissingArt = new dev.phatanon.entity.Song("Title 2", "Artist 2", "url 2");
            songWithMissingArt.setDurationSeconds(120);
            songWithMissingArt.setCoverArt(null);

            dev.phatanon.entity.Song songComplete = new dev.phatanon.entity.Song("Title 3", "Artist 3", "url 3");
            songComplete.setDurationSeconds(180);
            songComplete.setCoverArt("existing-art-3");

            when(songRepository.findAll()).thenReturn(java.util.List.of(songWithMissingDuration, songWithMissingArt, songComplete));
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
            when(twitchBotService.isStreamerConnected()).thenReturn(true);
            when(twitchBotService.isBotConnected()).thenReturn(true);

            ConnectionStartupLogger logger = new ConnectionStartupLogger(jdbcTemplate, twitchBotService, songRepository, songService, userService);
            ReflectionTestUtils.setField(logger, "buildId", "test-build-id");

            // Act
            logger.run();

            // Assert
            // Verify metadata update was called for songs with missing data
            org.mockito.Mockito.verify(songService).updateMetadata(songWithMissingDuration);
            org.mockito.Mockito.verify(songService).updateMetadata(songWithMissingArt);
            // Verify songComplete was NOT updated
            org.mockito.Mockito.verify(songService, org.mockito.Mockito.never()).updateMetadata(songComplete);
        }
    }
}
