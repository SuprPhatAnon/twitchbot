package dev.phatanon.controller;

import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing {@link TwitchConfig} entities and Twitch-related status.
 */
@RestController
@RequestMapping("/api/twitch-config")
@Tag(name = "Twitch Configuration", description = "Endpoints for managing Twitch bot credentials and settings")
@SecurityRequirement(name = "X-API-Key")
public class TwitchConfigController {

    private final TwitchConfigRepository twitchConfigRepository;
    private final TwitchBotService twitchBotService;

    public TwitchConfigController(TwitchConfigRepository twitchConfigRepository, TwitchBotService twitchBotService) {
        this.twitchConfigRepository = twitchConfigRepository;
        this.twitchBotService = twitchBotService;
    }

    /**
     * Retrieves recent channel point redeems.
     * @return A list of {@link TwitchBotService.RedeemLog} objects.
     */
    @GetMapping("/redeems")
    @Operation(summary = "Get recent channel point redeems")
    public List<TwitchBotService.RedeemLog> getRecentRedeems() {
        return twitchBotService.getRecentRedeems();
    }

    /**
     * Retrieves current stream status (online/offline).
     * @return true if stream is online, false otherwise.
     */
    @GetMapping("/status")
    @Operation(summary = "Get current stream online/offline status")
    public ResponseEntity<Boolean> getStreamStatus() {
        return ResponseEntity.ok(twitchBotService.isStreamOnline());
    }

    /**
     * Retrieves the current Twitch configuration.
     * @return The Twitch configuration if found, or 404 Not Found.
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
     * Updates the Twitch configuration.
     * @param config The new configuration details.
     * @return The updated configuration.
     */
    @PutMapping
    @Operation(summary = "Update Twitch configuration")
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
}
