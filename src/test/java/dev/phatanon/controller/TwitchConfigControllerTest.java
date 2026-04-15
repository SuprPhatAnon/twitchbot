package dev.phatanon.controller;

import dev.phatanon.dto.TwitchConfigDTO;
import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwitchConfigControllerTest {

    @Mock
    private TwitchConfigRepository twitchConfigRepository;
    @Mock
    private TwitchBotService twitchBotService;
    @Mock
    private Environment environment;

    private TwitchConfigController twitchConfigController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        twitchConfigController = new TwitchConfigController(twitchConfigRepository, twitchBotService, environment);
    }

    @Test
    void getRecentRedeems_CallsService() {
        twitchConfigController.getRecentRedeems();
        verify(twitchBotService).getRecentRedeems();
    }

    @Test
    void getStreamStatus_ReturnsStatus() {
        when(twitchBotService.isStreamOnline()).thenReturn(true);
        ResponseEntity<Boolean> response = twitchConfigController.getStreamStatus();
        assertTrue(response.getBody());
    }

    @Test
    void getConnectionStatus_ReturnsStatus() {
        when(twitchBotService.isTwitchConnected()).thenReturn(true);
        ResponseEntity<Boolean> response = twitchConfigController.getConnectionStatus();
        assertTrue(response.getBody());
    }

    @Test
    void getConfig_ReturnsConfig() {
        TwitchConfig config = new TwitchConfig();
        config.setClientId("test-client");
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));
        ResponseEntity<TwitchConfigDTO> response = twitchConfigController.getConfig();
        assertEquals("test-client", response.getBody().getClientId());
    }

    @Test
    void getConfig_NotFound_Returns404() {
        when(twitchConfigRepository.findAll()).thenReturn(List.of());
        ResponseEntity<TwitchConfigDTO> response = twitchConfigController.getConfig();
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void updateConfig_Existing_Updates() {
        TwitchConfig existing = new TwitchConfig();
        existing.setId(1L);
        existing.setClientSecret("secret");
        TwitchConfig newConfig = new TwitchConfig();
        newConfig.setClientSecret("********");
        
        when(twitchConfigRepository.findAll()).thenReturn(List.of(existing));
        when(twitchConfigRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        TwitchConfigDTO result = twitchConfigController.updateConfig(newConfig);

        assertEquals(1L, result.getId());
        verify(twitchConfigRepository).save(argThat(c -> "secret".equals(c.getClientSecret())));
        verify(twitchBotService).reconnect();
    }

    @Test
    void updateConfig_New_Saves() {
        TwitchConfig newConfig = new TwitchConfig();
        newConfig.setClientId("new-client");
        when(twitchConfigRepository.findAll()).thenReturn(List.of());
        when(twitchConfigRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        TwitchConfigDTO result = twitchConfigController.updateConfig(newConfig);

        assertEquals("new-client", result.getClientId());
        verify(twitchBotService).reconnect();
    }

    @Test
    void getActiveProfiles_ReturnsProfiles() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        List<String> profiles = twitchConfigController.getActiveProfiles();
        assertEquals(1, profiles.size());
        assertEquals("prod", profiles.get(0));
    }
}
