package dev.phatanon.controller;

import dev.phatanon.dto.TwitchConfigDTO;
import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "basicAuth")
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
     * @return The {@link TwitchConfigDTO} if found, or 404 Not Found.
     */
    @GetMapping
    @Operation(summary = "Get current Twitch configuration")
    public ResponseEntity<TwitchConfigDTO> getConfig() {
        return twitchConfigRepository.findAll().stream()
                .findFirst()
                .map(config -> ResponseEntity.ok(TwitchConfigDTO.fromEntity(config)))
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update Twitch configuration")
    public TwitchConfigDTO updateConfig(@RequestBody TwitchConfig config) {
        // We only ever want one configuration row
        TwitchConfig existingConfig = twitchConfigRepository.findAll().stream().findFirst().orElse(null);
        
        if (existingConfig != null) {
            config.setId(existingConfig.getId());
            // If tokens are masked in the request, preserve existing ones
            if ("********".equals(config.getClientSecret())) config.setClientSecret(existingConfig.getClientSecret());
            if ("********".equals(config.getAccessToken())) config.setAccessToken(existingConfig.getAccessToken());
            if ("********".equals(config.getRefreshToken())) config.setRefreshToken(existingConfig.getRefreshToken());
            if ("********".equals(config.getBotAccessToken())) config.setBotAccessToken(existingConfig.getBotAccessToken());
            if ("********".equals(config.getBotRefreshToken())) config.setBotRefreshToken(existingConfig.getBotRefreshToken());
        }

        TwitchConfig saved = twitchConfigRepository.save(config);
        
        // Trigger reconnection with the new configuration
        twitchBotService.reconnect();
        
        return TwitchConfigDTO.fromEntity(saved);
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
    @PostMapping("/sync-eventsub")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manually trigger EventSub sync")
    public ResponseEntity<Void> syncEventSub() {
        twitchBotService.syncEventSub();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test-redeem")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Simulate a Twitch redeem for testing")
    public ResponseEntity<Void> testRedeem(@RequestParam String title) {
        twitchBotService.simulateRedeem(title);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/test-connection")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Test Twitch connection with current credentials")
    public ResponseEntity<Boolean> testConnection() {
        return ResponseEntity.ok(twitchBotService.testConnection());
    }

    @PostMapping("/refresh-tokens")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Manually refresh Twitch tokens")
    public ResponseEntity<Void> refreshTokens() {
        twitchBotService.refreshTokens();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/chat")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send a chat message to the configured Twitch channel")
    public ResponseEntity<Void> sendChatMessage(@RequestParam String message) {
        twitchBotService.sendChatMessage(message);
        return ResponseEntity.ok().build();
    }
}
