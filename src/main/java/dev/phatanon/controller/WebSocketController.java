package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {

    private final TwitchBotService twitchBotService;

    public WebSocketController(TwitchBotService twitchBotService) {
        this.twitchBotService = twitchBotService;
    }

    @MessageMapping("/song-finished")
    public void songFinished() {
        twitchBotService.handleSongFinished();
    }
}
