package dev.phatanon.controller;

import dev.phatanon.dto.TwitchConfigDTO;
import dev.phatanon.dto.TwitchStatusDTO;
import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * REST controller for managing {@link TwitchConfig} entities and Twitch-related status.
 */
@RestController
@RequestMapping("/api/twitch-config")
@Tag(name = "Twitch Configuration", description = "Endpoints for managing Twitch bot credentials and settings")
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "basicAuth")
public class TwitchConfigController {

    private final Environment environment;
    private final TwitchBotService twitchBotService;
    private static final Logger log = LoggerFactory.getLogger(TwitchConfigController.class);

    private final TwitchConfigRepository twitchConfigRepository;

    @Value("${twitch.redirect-uri-host:https://music.phat.wtf}")
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

    @GetMapping("/full-status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get full Twitch status (stream, connection, subscriptions)")
    public ResponseEntity<TwitchStatusDTO> getFullStatus() {
        TwitchStatusDTO status = new TwitchStatusDTO();
        status.setStreamOnline(twitchBotService.isStreamOnline());
        status.setStreamerConnected(twitchBotService.isStreamerConnected());
        status.setBotConnected(twitchBotService.isBotConnected());
        status.setSubscriptionStatuses(twitchBotService.getSubscriptionStatuses());
        return ResponseEntity.ok(status);
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
     * Retrieves the current Streamer connection status from the {@link TwitchBotService}.
     * @return true if the Streamer EventSub client is connected, false otherwise.
     */
    @GetMapping("/connection/streamer")
    @Operation(summary = "Get current Streamer connection status")
    public ResponseEntity<Boolean> getStreamerConnectionStatus() {
        return ResponseEntity.ok(twitchBotService.isStreamerConnected());
    }

    /**
     * Retrieves the current Twitch connection status from the {@link TwitchBotService}.
     * @return true if the Twitch EventSub client is connected, false otherwise.
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
            // Apply updates to the existing config, but only for non-null/non-empty fields in the incoming config
            if (config.getClientId() != null) existingConfig.setClientId(config.getClientId());
            if (config.getChannelName() != null) existingConfig.setChannelName(config.getChannelName());
            if (config.getSongDelaySeconds() > 0) existingConfig.setSongDelaySeconds(config.getSongDelaySeconds());

            // Handle sensitive fields (tokens and secret)
            // If tokens are masked in the request, preserve existing ones
            // If they are null, preserve existing ones
            if (config.getClientSecret() != null && !"********".equals(config.getClientSecret())) 
                existingConfig.setClientSecret(config.getClientSecret());

            if (config.getAccessToken() != null && !"********".equals(config.getAccessToken())) 
                existingConfig.setAccessToken(config.getAccessToken());
                
            if (config.getRefreshToken() != null && !"********".equals(config.getRefreshToken())) 
                existingConfig.setRefreshToken(config.getRefreshToken());
                
            if (config.getBotAccessToken() != null && !"********".equals(config.getBotAccessToken())) 
                existingConfig.setBotAccessToken(config.getBotAccessToken());
                
            if (config.getBotRefreshToken() != null && !"********".equals(config.getBotRefreshToken())) 
                existingConfig.setBotRefreshToken(config.getBotRefreshToken());
            
            if (config.getWebhookSecret() != null && !"********".equals(config.getWebhookSecret()))
                existingConfig.setWebhookSecret(config.getWebhookSecret());
            
            // Re-assign the config variable to the modified existingConfig for saving
            config = existingConfig;
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

    /**
     * Redirects to Twitch for OAuth authorization.
     * @param type The type of account to authorize ('streamer' or 'bot').
     * @return A redirect to Twitch's authorization page.
     */
    @GetMapping("/authorize")
    @Operation(summary = "Redirect to Twitch for OAuth authorization")
    public ResponseEntity<Void> authorize(@RequestParam(defaultValue = "streamer") String type) {
        TwitchConfig config = twitchConfigRepository.findAll().stream().findFirst().orElse(null);
        if (config == null || config.getClientId() == null || config.getClientId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String scopes;
        if ("bot".equals(type)) {
            // Scopes for bot
            scopes = "user:bot user:read:chat user:write:chat chat:read chat:edit";
        } else {
            // Scopes for streamer
            scopes = "channel:read:redemptions channel:read:subscriptions moderator:read:followers bits:read chat:read chat:edit channel:bot channel:manage:redemptions";
        }

        String redirectUri = redirectUriHost + "/api/twitch-config/callback";
        String url = String.format(
                "https://id.twitch.tv/oauth2/authorize?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s",
                config.getClientId(),
                java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8),
                java.net.URLEncoder.encode(scopes, java.nio.charset.StandardCharsets.UTF_8),
                type
        );

        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .location(java.net.URI.create(url))
                .build();
    }

    /**
     * Callback for Twitch OAuth.
     * Exchanges the authorization code for tokens and saves them.
     */
    @GetMapping("/callback")
    @Operation(summary = "Twitch OAuth callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        TwitchConfig config = twitchConfigRepository.findAll().stream().findFirst().orElse(null);
        if (config == null || config.getClientId() == null || config.getClientSecret() == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        try {
            String redirectUri = redirectUriHost + "/api/twitch-config/callback";
            com.github.twitch4j.auth.providers.TwitchIdentityProvider identityProvider = new com.github.twitch4j.auth.providers.TwitchIdentityProvider(
                    config.getClientId(),
                    config.getClientSecret(),
                    redirectUri
            );
            
            com.github.philippheuer.credentialmanager.domain.OAuth2Credential userCredential = identityProvider.getCredentialByCode(code);

            if (userCredential != null) {
                if ("bot".equals(state)) {
                    log.info("[AUTH] Received authorization code for bot account. Updating tokens.");
                    config.setBotAccessToken(userCredential.getAccessToken());
                    config.setBotRefreshToken(userCredential.getRefreshToken());
                } else {
                    log.info("[AUTH] Received authorization code for streamer account. Updating tokens.");
                    config.setAccessToken(userCredential.getAccessToken());
                    config.setRefreshToken(userCredential.getRefreshToken());
                }
                twitchConfigRepository.save(config);
                log.info("[AUTH] Twitch configuration updated with new tokens and saved to database.");
                twitchBotService.reconnect();
            }

            return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                    .location(java.net.URI.create("/admin.html"))
                    .build();
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
