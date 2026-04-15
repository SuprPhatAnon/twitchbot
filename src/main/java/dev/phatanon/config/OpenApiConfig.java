package dev.phatanon.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OpenAPI/Swagger documentation.
 * Defines security schemes for API Key and Basic Authentication.
 */
@Configuration
@SecurityScheme(
        name = "apiKey",
        type = SecuritySchemeType.APIKEY,
        in = io.swagger.v3.oas.annotations.enums.SecuritySchemeIn.HEADER,
        paramName = "X-API-KEY"
)
@SecurityScheme(
        name = "basicAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "basic"
)
public class OpenApiConfig {

    /**
     * Customizes the OpenAPI definition.
     * @return The configured OpenAPI object.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Twitch Song Overlay Bot API")
                        .version("1.0")
                        .description("REST API for managing the Twitch Song Overlay Bot. " +
                                "Write requests (POST, PUT, DELETE) and protected GET requests require " +
                                "API Key authentication via the X-API-KEY header."));
    }
}
