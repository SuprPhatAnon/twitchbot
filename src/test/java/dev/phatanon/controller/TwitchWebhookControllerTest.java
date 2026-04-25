package dev.phatanon.controller;

import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TwitchWebhookControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TwitchBotService twitchBotService;

    @Mock
    private TwitchConfigRepository twitchConfigRepository;

    private ObjectMapper objectMapper = new ObjectMapper();

    private TwitchWebhookController twitchWebhookController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        twitchWebhookController = new TwitchWebhookController(twitchBotService, twitchConfigRepository, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(twitchWebhookController).build();
    }

    @Test
    void handleCallback_Verification_ReturnsChallenge() throws Exception {
        TwitchConfig config = new TwitchConfig();
        config.setWebhookSecret("test-secret");
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));

        String body = "{\"challenge\": \"pogchamp-challenge-123\", \"subscription\": {\"type\": \"channel.subscribe\"}}";
        String messageId = "msg-123";
        String timestamp = "2023-01-01T00:00:00Z";
        
        // Manual HMAC calculation for test
        String hmacMessage = messageId + timestamp + body;
        javax.crypto.Mac sha256_HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec secret_key = new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(hmacMessage.getBytes());
        StringBuilder sb = new StringBuilder("sha256=");
        for (byte b : hash) sb.append(String.format("%02x", b));
        String signature = sb.toString();

        mockMvc.perform(post("/api/twitch/callback")
                .header("Twitch-Eventsub-Message-Id", messageId)
                .header("Twitch-Eventsub-Message-Timestamp", timestamp)
                .header("Twitch-Eventsub-Message-Signature", signature)
                .header("Twitch-Eventsub-Message-Type", "webhook_callback_verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("pogchamp-challenge-123"));
    }

    @Test
    void handleCallback_InvalidSignature_ReturnsForbidden() throws Exception {
        TwitchConfig config = new TwitchConfig();
        config.setWebhookSecret("test-secret");
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));

        mockMvc.perform(post("/api/twitch/callback")
                .header("Twitch-Eventsub-Message-Id", "id")
                .header("Twitch-Eventsub-Message-Timestamp", "ts")
                .header("Twitch-Eventsub-Message-Signature", "sha256=invalid")
                .header("Twitch-Eventsub-Message-Type", "notification")
                .content("{}"))
                .andExpect(status().isForbidden());
    }
    @Test
    void handleCallback_Notification_ProcessedSuccessfully() throws Exception {
        TwitchConfig config = new TwitchConfig();
        config.setWebhookSecret("test-secret");
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));
        
        com.github.twitch4j.TwitchClient mockClient = mock(com.github.twitch4j.TwitchClient.class);
        com.github.philippheuer.events4j.core.EventManager mockEventManager = mock(com.github.philippheuer.events4j.core.EventManager.class);
        when(twitchBotService.getTwitchClient()).thenReturn(mockClient);
        when(mockClient.getEventManager()).thenReturn(mockEventManager);

        String messageId = "j4g_fX-rW6OF2t7NNs3Zp5v7Ny7e1Q7co9RkU-nKhZw=";
        String timestamp = "2026-04-25T17:51:24.439541216Z";
        String messageType = "notification";
        String body = "{\"subscription\":{\"id\":\"84bade8e-4ab6-4a98-9cf3-df9a696ff5df\",\"status\":\"enabled\",\"type\":\"channel.chat.message\",\"version\":\"1\",\"condition\":{\"broadcaster_user_id\":\"103375464\",\"user_id\":\"611325223\"},\"transport\":{\"method\":\"webhook\",\"callback\":\"https://music.phat.wtf/api/twitch/callback\"},\"created_at\":\"2026-04-25T17:46:31.568853771Z\",\"cost\":0},\"event\":{\"broadcaster_user_id\":\"103375464\",\"broadcaster_user_login\":\"giantthumb\",\"broadcaster_user_name\":\"GiantThumb\",\"source_broadcaster_user_id\":null,\"source_broadcaster_user_login\":null,\"source_broadcaster_user_name\":null,\"chatter_user_id\":\"29918156\",\"chatter_user_login\":\"phatanon\",\"chatter_user_name\":\"PhatAnon\",\"message_id\":\"bcf7ce24-81f6-49f5-9acf-c166f5e95b74\",\"source_message_id\":null,\"is_source_only\":null,\"message\":{\"text\":\"test21\",\"fragments\":[{\"type\":\"text\",\"text\":\"test21\",\"cheermote\":null,\"emote\":null,\"mention\":null}]},\"color\":\"#5BBCA7\",\"badges\":[{\"set_id\":\"subscriber\",\"id\":\"12\",\"info\":\"29\"},{\"set_id\":\"nasa-artemis-ii\",\"id\":\"1\",\"info\":\"\"}],\"source_badges\":null,\"message_type\":\"text\",\"cheer\":null,\"reply\":null,\"channel_points_custom_reward_id\":null,\"channel_points_animation_id\":null}}";

        // Calculate HMAC
        String hmacMessage = messageId + timestamp + body;
        javax.crypto.Mac sha256_HMAC = javax.crypto.Mac.getInstance("HmacSHA256");
        javax.crypto.spec.SecretKeySpec secret_key = new javax.crypto.spec.SecretKeySpec("test-secret".getBytes(), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(hmacMessage.getBytes());
        StringBuilder sb = new StringBuilder("sha256=");
        for (byte b : hash) sb.append(String.format("%02x", b));
        String signature = sb.toString();

        mockMvc.perform(post("/api/twitch/callback")
                .header("Twitch-Eventsub-Message-Id", messageId)
                .header("Twitch-Eventsub-Message-Timestamp", timestamp)
                .header("Twitch-Eventsub-Message-Signature", signature)
                .header("Twitch-Eventsub-Message-Type", messageType)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }
}
