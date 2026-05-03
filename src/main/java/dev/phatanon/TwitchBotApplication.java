package dev.phatanon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Main entry point for the Twitch Bot application.
 * This class initializes the Spring Boot application context.
 */
@SpringBootApplication
public class TwitchBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TwitchBotApplication.class, args);
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newSingleThreadScheduledExecutor();
    }
}
