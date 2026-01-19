package dev.phatanon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.connection.RedisConnectionFactory;
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
    @Import(TestRedisConfiguration.class)
    @ExtendWith(OutputCaptureExtension.class)
    class SuccessTests {
        @Test
        void testConnectionLogging(CapturedOutput output) {
            assertThat(output.getOut()).contains("Starting connection acquisition checks...");
            assertThat(output.getOut()).contains("✅ Successfully connected to MariaDB.");
            assertThat(output.getOut()).contains("✅ Successfully connected to Redis.");
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
            RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
            
            when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                    .thenThrow(new RuntimeException("Connection refused"));
            
            // Act
            ConnectionStartupLogger logger = new ConnectionStartupLogger(jdbcTemplate, redisConnectionFactory);
            logger.run();

            // Assert
            assertThat(output.getOut()).contains("❌ Error acquiring MariaDB connection: Connection refused");
        }

        @Test
        void testRedisConnectionFailure(CapturedOutput output) {
            // Arrange
            JdbcOperations jdbcTemplate = mock(JdbcOperations.class);
            RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
            
            when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Redis unavailable"));

            // Act
            ConnectionStartupLogger logger = new ConnectionStartupLogger(jdbcTemplate, redisConnectionFactory);
            logger.run();

            // Assert
            assertThat(output.getOut()).contains("❌ Error acquiring Redis connection: Redis unavailable");
        }

        @Test
        void testRedisWrongPingResponse(CapturedOutput output) {
            // Arrange
            JdbcOperations jdbcTemplate = mock(JdbcOperations.class);
            RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
            
            when(redisConnectionFactory.getConnection()).thenReturn(null);

            // Act
            ConnectionStartupLogger logger = new ConnectionStartupLogger(jdbcTemplate, redisConnectionFactory);
            logger.run();

            // Assert
            assertThat(output.getOut()).contains("❌ Error acquiring Redis connection");
        }
    }
}
