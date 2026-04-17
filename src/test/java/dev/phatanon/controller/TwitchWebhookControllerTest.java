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
}
