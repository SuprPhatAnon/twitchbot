package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@Tag(name = "Test Endpoints", description = "Endpoints for manual testing")
public class TestController {

    private final TwitchBotService twitchBotService;

    public TestController(TwitchBotService twitchBotService) {
        this.twitchBotService = twitchBotService;
    }

    @GetMapping("/play")
    @Operation(summary = "Trigger a random song to play")
    public String triggerPlay() {
        twitchBotService.playRandomSong();
        return "Song triggered!";
    }

    @GetMapping("/finish")
    @Operation(summary = "Trigger song finished (simulates frontend callback)")
    public String triggerFinish() {
        twitchBotService.handleSongFinished();
        return "Song finish triggered!";
    }
}
