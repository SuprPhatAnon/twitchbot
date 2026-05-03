package dev.phatanon.controller;

import dev.phatanon.dto.RainEffectDTO;
import dev.phatanon.service.TwitchBotService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Controller for handling WebSocket messages.
 */
@Controller
public class WebSocketController {

    private final TwitchBotService twitchBotService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketController(TwitchBotService twitchBotService, SimpMessagingTemplate messagingTemplate) {
        this.twitchBotService = twitchBotService;
        this.messagingTemplate = messagingTemplate;
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

    /**
     * Endpoint for triggering a rain effect on the overlay.
     */
    @MessageMapping("/trigger-rain")
    public void triggerRain(@Payload RainEffectDTO rainEffect) {
        messagingTemplate.convertAndSend("/topic/rain", rainEffect);
    }
}
