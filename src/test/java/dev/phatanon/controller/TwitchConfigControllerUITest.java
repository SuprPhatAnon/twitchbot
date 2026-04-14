package dev.phatanon.controller;

import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class TwitchConfigControllerUITest {

    private MockMvc mockMvc;

    @Mock
    private TwitchBotService twitchBotService;

    @Mock
    private dev.phatanon.repository.TwitchConfigRepository twitchConfigRepository;

    @Mock
    private org.springframework.core.env.Environment environment;

    @InjectMocks
    private TwitchConfigController twitchConfigController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(twitchConfigController).build();
    }

    @Test
    void shouldGetStreamStatusForUI() throws Exception {
        when(twitchBotService.isStreamOnline()).thenReturn(true);

        mockMvc.perform(get("/api/twitch-config/status"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        when(twitchBotService.isStreamOnline()).thenReturn(false);

        mockMvc.perform(get("/api/twitch-config/status"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void shouldGetConnectionStatusForUI() throws Exception {
        when(twitchBotService.isTwitchConnected()).thenReturn(true);

        mockMvc.perform(get("/api/twitch-config/connection"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        when(twitchBotService.isTwitchConnected()).thenReturn(false);

        mockMvc.perform(get("/api/twitch-config/connection"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void shouldGetRecentRedeemsForUI() throws Exception {
        TwitchBotService.RedeemLog log = new TwitchBotService.RedeemLog("user1", "reward1", LocalDateTime.now());
        when(twitchBotService.getRecentRedeems()).thenReturn(List.of(log));

        mockMvc.perform(get("/api/twitch-config/redeems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].user", is("user1")))
                .andExpect(jsonPath("$[0].rewardTitle", is("reward1")));
    }
}
