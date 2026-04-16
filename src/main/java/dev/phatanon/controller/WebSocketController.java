package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
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
    public void songFinished(@Payload(required = false) String payload) {
        twitchBotService.handleSongFinished();
    }

    /**
     * Endpoint for requesting the current state (currently playing song, queue size, etc.)
     */
    @MessageMapping("/request-state")
    public void requestState() {
        twitchBotService.broadcastCurrentSong();
        twitchBotService.broadcastQueueSize();
    }
}
