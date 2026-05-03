package dev.phatanon.controller;

import dev.phatanon.dto.RainEffectDTO;
import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

class WebSocketControllerTest {

    @Mock
    private TwitchBotService twitchBotService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WebSocketController webSocketController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        webSocketController = new WebSocketController(twitchBotService, messagingTemplate);
    }

    @Test
    void songFinished_CallsTwitchBotService() {
        webSocketController.songFinished("any payload");
        verify(twitchBotService).handleSongFinished();
    }

    @Test
    void triggerRain_BroadcastsToTopic() {
        RainEffectDTO rainEffect = new RainEffectDTO("🌧️", 5000);
        webSocketController.triggerRain(rainEffect);
        verify(messagingTemplate).convertAndSend(eq("/topic/rain"), eq(rainEffect));
    }
}
