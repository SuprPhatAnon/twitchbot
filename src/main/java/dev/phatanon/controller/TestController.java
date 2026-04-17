package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@Tag(name = "Test Endpoints", description = "Endpoints for manual testing")
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "basicAuth")
public class TestController {

    private final TwitchBotService twitchBotService;

    public TestController(TwitchBotService twitchBotService) {
        this.twitchBotService = twitchBotService;
    }

    /**
     * Triggers a random song to play through the {@link TwitchBotService}.
     * This is intended for manual testing of the overlay.
     * @return A success message.
     */
    @GetMapping("/play")
    @Operation(summary = "Trigger a random song to play")
    public String triggerPlay() {
        twitchBotService.playRandomSong();
        return "Song triggered!";
    }

    /**
     * Simulates a "song-finished" event from the frontend.
     * This triggers the next song in the queue (if any).
     * @return A success message.
     */
    @GetMapping("/finish")
    @Operation(summary = "Trigger song finished (simulates frontend callback)")
    public String triggerFinish() {
        twitchBotService.handleSongFinished();
        return "Song finish triggered!";
    }
}
