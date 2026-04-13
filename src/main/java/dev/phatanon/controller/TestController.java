package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final TwitchBotService twitchBotService;

    public TestController(TwitchBotService twitchBotService) {
        this.twitchBotService = twitchBotService;
    }

    @GetMapping("/play")
    public String triggerPlay() {
        twitchBotService.playRandomSong();
        return "Song triggered!";
    }
}
