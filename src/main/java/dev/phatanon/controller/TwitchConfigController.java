package dev.phatanon.controller;

import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.TwitchConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/twitch-config")
@Tag(name = "Twitch Configuration", description = "Endpoints for managing Twitch bot credentials and settings")
public class TwitchConfigController {

    private final TwitchConfigRepository twitchConfigRepository;

    public TwitchConfigController(TwitchConfigRepository twitchConfigRepository) {
        this.twitchConfigRepository = twitchConfigRepository;
    }

    @GetMapping
    @Operation(summary = "Get current Twitch configuration")
    public ResponseEntity<TwitchConfig> getConfig() {
        return twitchConfigRepository.findAll().stream()
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

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
