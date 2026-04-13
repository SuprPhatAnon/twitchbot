package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/**
 * Controller for handling WebSocket messages.
 */
@Controller
public class WebSocketController {

    private final TwitchBotService twitchBotService;

    public WebSocketController(TwitchBotService twitchBotService) {
        this.twitchBotService = twitchBotService;
    }

    /**
     * Endpoint for receiving "song-finished" messages from the frontend.
     * Triggers the next song in the queue.
     */
    @MessageMapping("/song-finished")
    public void songFinished() {
        twitchBotService.handleSongFinished();
    }
}
