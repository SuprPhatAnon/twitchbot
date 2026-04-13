package dev.phatanon;

import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
class ConnectionStartupLoggerTests {

    @Nested
    @SpringBootTest
    @ExtendWith(OutputCaptureExtension.class)
    class SuccessTests {
        @Test
        void testConnectionLogging(CapturedOutput output) {
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
            // Using a simple lambda to mock the service behavior without ByteBuddy issues on the class
            ConnectionStartupLogger.ITwitchBotService twitchBotService = () -> false;
            
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                    .thenThrow(new RuntimeException("Connection refused"));
            
            // Act
            ConnectionStartupLogger logger = new ConnectionStartupLogger(jdbcTemplate, twitchBotService);
            logger.run();

            // Assert
            assertThat(output.getOut()).contains("❌ Error acquiring MariaDB connection: Connection refused");
        }
    }
}
