package dev.phatanon.controller;

import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for managing {@link TwitchConfig} entities and Twitch-related status.
 */
@RestController
@RequestMapping("/api/twitch-config")
@Tag(name = "Twitch Configuration", description = "Endpoints for managing Twitch bot credentials and settings")
public class TwitchConfigController {

    private final TwitchConfigRepository twitchConfigRepository;
    private final TwitchBotService twitchBotService;
    private final Environment environment;

    @Value("${twitch.redirect-uri-host:https://stream.phat.wtf}")
    private String redirectUriHost;

    public TwitchConfigController(TwitchConfigRepository twitchConfigRepository, TwitchBotService twitchBotService, Environment environment) {
        this.twitchConfigRepository = twitchConfigRepository;
        this.twitchBotService = twitchBotService;
        this.environment = environment;
    }

    /**
     * Retrieves recent channel point redeems from the {@link TwitchBotService}.
     * @return A list of {@link TwitchBotService.RedeemLog} objects representing recent events.
     */
    @GetMapping("/redeems")
    @Operation(summary = "Get recent channel point redeems")
    public List<TwitchBotService.RedeemLog> getRecentRedeems() {
        return twitchBotService.getRecentRedeems();
    }

    /**
     * Retrieves current stream status (online/offline) from the {@link TwitchBotService}.
     * The status is tracked via Twitch EventSub events.
     * @return true if the stream is currently online, false otherwise.
     */
    @GetMapping("/status")
    @Operation(summary = "Get current stream online/offline status")
    public ResponseEntity<Boolean> getStreamStatus() {
        return ResponseEntity.ok(twitchBotService.isStreamOnline());
    }

    /**
     * Retrieves the current Twitch connection status from the {@link TwitchBotService}.
     * @return true if the Twitch IRC client is connected, false otherwise.
     */
    @GetMapping("/connection")
    @Operation(summary = "Get current Twitch connection status")
    public ResponseEntity<Boolean> getConnectionStatus() {
        return ResponseEntity.ok(twitchBotService.isTwitchConnected());
    }

    /**
     * Retrieves the current Twitch configuration from the database.
     * Only one configuration entry is expected to exist.
     * @return The {@link TwitchConfig} if found, or 404 Not Found.
     */
    @GetMapping
    @Operation(summary = "Get current Twitch configuration")
    public ResponseEntity<TwitchConfig> getConfig() {
        return twitchConfigRepository.findAll().stream()
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates or creates the Twitch configuration in the database.
     * This method ensures that only a single configuration entry is maintained.
     * Requires an API key for authorization.
     * @param config The new configuration details to save.
     * @return The saved {@link TwitchConfig} object.
     */
    @PutMapping
    @Operation(summary = "Update Twitch configuration", security = @SecurityRequirement(name = "X-API-Key"))
    public TwitchConfig updateConfig(@RequestBody TwitchConfig config) {
        // We only ever want one configuration row
        return twitchConfigRepository.findAll().stream()
                .findFirst()
                .map(existing -> {
                    config.setId(existing.getId());
                    return twitchConfigRepository.save(config);
                })
                .orElseGet(() -> twitchConfigRepository.save(config));
    }

    /**
     * Retrieves the currently active Spring profiles.
     * @return A list of active profile names.
     */
    @GetMapping("/profiles")
    @Operation(summary = "Get active Spring profiles")
    public List<String> getActiveProfiles() {
        return Arrays.asList(environment.getActiveProfiles());
    }

    /**
     * Retrieves the configured redirect URI host.
     * @return The redirect URI host (e.g., http://localhost:8080 or https://mybot.com).
     */
    @GetMapping("/redirect-uri-host")
    @Operation(summary = "Get the configured redirect URI host")
    public ResponseEntity<String> getRedirectUriHost() {
        return ResponseEntity.ok(redirectUriHost);
    }
}
