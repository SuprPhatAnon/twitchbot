package dev.phatanon.controller;

import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void shouldGetConfig() throws Exception {
        TwitchConfig config = new TwitchConfig();
        config.setId(1L);
        config.setClientId("test-client-id");

        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));

        mockMvc.perform(get("/api/twitch-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId", is("test-client-id")));
    }

    @Test
    void shouldReturnNotFoundWhenNoConfigExists() throws Exception {
        when(twitchConfigRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/twitch-config"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateExistingConfig() throws Exception {
        TwitchConfig existing = new TwitchConfig();
        existing.setId(1L);

        TwitchConfig updated = new TwitchConfig();
        updated.setClientId("new-client-id");

        when(twitchConfigRepository.findAll()).thenReturn(List.of(existing));
        when(twitchConfigRepository.save(any(TwitchConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/twitch-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\": \"new-client-id\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId", is("new-client-id")))
                .andExpect(jsonPath("$.id", is(1)));

        verify(twitchBotService).reconnect();
    }

    @Test
    void shouldCreateNewConfigWhenNoneExists() throws Exception {
        TwitchConfig config = new TwitchConfig();
        config.setClientId("new-client-id");

        when(twitchConfigRepository.findAll()).thenReturn(List.of());
        when(twitchConfigRepository.save(any(TwitchConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/twitch-config")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\": \"new-client-id\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId", is("new-client-id")));

        verify(twitchBotService).reconnect();
    }

    @Test
    void shouldGetActiveProfiles() throws Exception {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod", "mysql"});

        mockMvc.perform(get("/api/twitch-config/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]", is("prod")))
                .andExpect(jsonPath("$[1]", is("mysql")));
    }

    @Test
    void shouldGetRedirectUriHost() throws Exception {
        mockMvc.perform(get("/api/twitch-config/redirect-uri-host"))
                .andExpect(status().isOk());
    }
}
