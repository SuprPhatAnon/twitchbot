package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TestControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TwitchBotService twitchBotService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private TestController testController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(testController).build();
    }

    @Test
    void triggerPlay_CallsTwitchBotService() throws Exception {
        mockMvc.perform(get("/api/test/play"))
                .andExpect(status().isOk())
                .andExpect(content().string("Song triggered!"));
        verify(twitchBotService).playRandomSong();
    }

    @Test
    void triggerFinish_CallsTwitchBotService() throws Exception {
        mockMvc.perform(get("/api/test/finish"))
                .andExpect(status().isOk())
                .andExpect(content().string("Song finish triggered!"));
        verify(twitchBotService).handleSongFinished();
    }

    @Test
    void triggerRain_CallsMessagingTemplate() throws Exception {
        mockMvc.perform(get("/api/test/rain")
                        .param("content", "🔥")
                        .param("duration", "3000"))
                .andExpect(status().isOk());
        verify(messagingTemplate).convertAndSend(eq("/topic/rain"), any(Object.class));
    }
}
