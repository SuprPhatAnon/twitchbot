package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

class WebSocketControllerTest {

    @Mock
    private TwitchBotService twitchBotService;

    private WebSocketController webSocketController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        webSocketController = new WebSocketController(twitchBotService);
    }

    @Test
    void songFinished_CallsTwitchBotService() {
        webSocketController.songFinished("any payload");
        verify(twitchBotService).handleSongFinished();
    }
}
