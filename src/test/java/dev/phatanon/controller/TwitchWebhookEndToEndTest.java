package dev.phatanon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TwitchWebhookEndToEndTest {

    private MockMvc mockMvc;

    @Mock
    private TwitchBotService twitchBotService;

    @Mock
    private TwitchConfigRepository twitchConfigRepository;

    @Mock
    private SongRepository songRepository;

    @Mock
    private TwitchClient twitchClient;

    @Mock
    private EventManager eventManager;

    private ObjectMapper objectMapper = spy(new ObjectMapper());

    private TwitchWebhookController twitchWebhookController;

    private static final String WEBHOOK_SECRET = "test-secret";

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        twitchWebhookController = new TwitchWebhookController(twitchBotService, twitchConfigRepository, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(twitchWebhookController).build();

        TwitchConfig config = new TwitchConfig();
        config.setWebhookSecret(WEBHOOK_SECRET);
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));

        when(twitchBotService.getTwitchClient()).thenReturn(twitchClient);
        when(twitchClient.getEventManager()).thenReturn(eventManager);
    }

    @Test
    void testWebhookFlow_WhenParsingFailsButValidHeadersProvided() throws Exception {
        // This test ensures that the controller validates headers and secret correctly
        // and returns 200 even if it can't parse the body (to stop Twitch retries)
        String body = "{\"subscription\": {}, \"event\": {}}";
        String messageId = "msg-test-123";
        String timestamp = "2023-01-01T00:00:00Z";
        String signature = calculateSignature(WEBHOOK_SECRET, messageId, timestamp, body);

        mockMvc.perform(post("/api/twitch/callback")
                .header("Twitch-Eventsub-Message-Id", messageId)
                .header("Twitch-Eventsub-Message-Timestamp", timestamp)
                .header("Twitch-Eventsub-Message-Signature", signature)
                .header("Twitch-Eventsub-Message-Type", "notification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    private String calculateSignature(String secret, String messageId, String timestamp, String body) throws Exception {
        String hmacMessage = messageId + timestamp + body;
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(hmacMessage.getBytes(StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder("sha256=");
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
