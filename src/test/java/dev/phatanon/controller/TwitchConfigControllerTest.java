package dev.phatanon.controller;

import dev.phatanon.dto.TwitchConfigDTO;
import dev.phatanon.dto.TwitchStatusDTO;
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
    void getFullStatus_ReturnsStatus() {
        when(twitchBotService.isStreamOnline()).thenReturn(true);
        when(twitchBotService.isStreamerConnected()).thenReturn(true);
        when(twitchBotService.isBotConnected()).thenReturn(false);
        when(twitchBotService.getSubscriptionStatuses()).thenReturn(java.util.Map.of("test", "ENABLED"));

        ResponseEntity<TwitchStatusDTO> response = twitchConfigController.getFullStatus();
        
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isStreamOnline());
        assertTrue(response.getBody().isStreamerConnected());
        assertFalse(response.getBody().isBotConnected());
        assertEquals(1, response.getBody().getSubscriptionStatuses().size());
        assertEquals("ENABLED", response.getBody().getSubscriptionStatuses().get("test"));
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
    void getStreamerConnectionStatus_ReturnsStatus() {
        when(twitchBotService.isStreamerConnected()).thenReturn(true);
        ResponseEntity<Boolean> response = twitchConfigController.getStreamerConnectionStatus();
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
    void updateConfig_PartialUpdate_PreservesExistingFields() {
        TwitchConfig existing = new TwitchConfig();
        existing.setId(1L);
        existing.setClientId("old-client-id");
        existing.setChannelName("old-channel");
        existing.setSongDelaySeconds(10);
        existing.setClientSecret("old-secret");

        TwitchConfig newConfig = new TwitchConfig();
        newConfig.setChannelName("new-channel");
        // Other fields like clientId, songDelaySeconds are not set (null/default)
        // Except songDelaySeconds which is primitive int in the entity (it will be 0 if not set)

        when(twitchConfigRepository.findAll()).thenReturn(List.of(existing));
        when(twitchConfigRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        TwitchConfigDTO result = twitchConfigController.updateConfig(newConfig);

        assertEquals("old-client-id", result.getClientId()); // This should be preserved
        assertEquals("new-channel", result.getChannelName()); // This should be updated
        assertEquals(10, result.getSongDelaySeconds()); // This should be preserved
        verify(twitchBotService).reconnect();
    }

    @Test
    void updateConfig_SensitiveFields_UpdateWhenNotNullAndNotMasked() {
        TwitchConfig existing = new TwitchConfig();
        existing.setId(1L);
        existing.setAccessToken("old-token");
        existing.setClientSecret("old-secret");

        TwitchConfig newConfig = new TwitchConfig();
        newConfig.setAccessToken("new-token");
        newConfig.setClientSecret("********"); // Masked, should not update

        when(twitchConfigRepository.findAll()).thenReturn(List.of(existing));
        when(twitchConfigRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        TwitchConfigDTO result = twitchConfigController.updateConfig(newConfig);

        verify(twitchConfigRepository).save(argThat(c -> 
            "new-token".equals(c.getAccessToken()) && 
            "old-secret".equals(c.getClientSecret())
        ));
    }

    @Test
    void getActiveProfiles_ReturnsProfiles() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        List<String> profiles = twitchConfigController.getActiveProfiles();
        assertEquals(1, profiles.size());
        assertEquals("prod", profiles.get(0));
    }

    @Test
    void sendChatMessage_CallsService() {
        String message = "hello chat";
        twitchConfigController.sendChatMessage(message);
        verify(twitchBotService).sendChatMessage(message);
    }

    @Test
    void authorize_WithNoClientId_ReturnsBadRequest() {
        when(twitchConfigRepository.findAll()).thenReturn(List.of());
        ResponseEntity<Void> response = twitchConfigController.authorize("streamer");
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void authorize_RedirectsToTwitch() {
        TwitchConfig config = new TwitchConfig();
        config.setClientId("test-client-id");
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));

        ResponseEntity<Void> response = twitchConfigController.authorize("streamer");

        assertEquals(302, response.getStatusCode().value());
        String location = response.getHeaders().getLocation().toString();
        assertTrue(location.contains("https://id.twitch.tv/oauth2/authorize"));
        assertTrue(location.contains("client_id=test-client-id"));
        assertTrue(location.contains("state=streamer"));
    }

    @Test
    void authorize_Bot_RedirectsWithBotState() {
        TwitchConfig config = new TwitchConfig();
        config.setClientId("test-client-id");
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));

        ResponseEntity<Void> response = twitchConfigController.authorize("bot");

        assertEquals(302, response.getStatusCode().value());
        String location = response.getHeaders().getLocation().toString();
        assertTrue(location.contains("state=bot"));
        assertTrue(location.contains("client_id=test-client-id"));
    }

    @Test
    void callback_HandlesNullConfig() {
        when(twitchConfigRepository.findAll()).thenReturn(List.of());
        ResponseEntity<Void> response = twitchConfigController.callback("code", "state");
        assertEquals(500, response.getStatusCode().value());
    }
}
