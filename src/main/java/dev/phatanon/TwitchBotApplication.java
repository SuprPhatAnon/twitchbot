package dev.phatanon;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Twitch Bot application.
 * This class initializes the Spring Boot application context.
 */
@SpringBootApplication
@SecurityScheme(
        name = "X-API-Key",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-API-Key"
)
public class TwitchBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TwitchBotApplication.class, args);
    }
}
