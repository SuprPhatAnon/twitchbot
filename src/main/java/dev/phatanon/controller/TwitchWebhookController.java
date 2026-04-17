package dev.phatanon.controller;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.eventsub.EventSubNotification;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/twitch/callback")
public class TwitchWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TwitchWebhookController.class);
    private final TwitchBotService twitchBotService;
    private final TwitchConfigRepository twitchConfigRepository;
    private final ObjectMapper objectMapper;

    private static final String MESSAGE_ID = "Twitch-Eventsub-Message-Id";
    private static final String MESSAGE_TIMESTAMP = "Twitch-Eventsub-Message-Timestamp";
    private static final String MESSAGE_SIGNATURE = "Twitch-Eventsub-Message-Signature";
    private static final String MESSAGE_TYPE = "Twitch-Eventsub-Message-Type";

    public TwitchWebhookController(TwitchBotService twitchBotService, TwitchConfigRepository twitchConfigRepository, ObjectMapper objectMapper) {
        this.twitchBotService = twitchBotService;
        this.twitchConfigRepository = twitchConfigRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<String> handleCallback(
            @RequestHeader(value = MESSAGE_ID, required = false) String messageId,
            @RequestHeader(value = MESSAGE_TIMESTAMP, required = false) String timestamp,
            @RequestHeader(value = MESSAGE_SIGNATURE, required = false) String signature,
            @RequestHeader(value = MESSAGE_TYPE, required = false) String messageType,
            @RequestBody String body) {

        log.debug("Received Twitch Webhook: ID={}, Type={}, Timestamp={}", messageId, messageType, timestamp);

        if (messageId == null || timestamp == null || signature == null || messageType == null) {
            log.warn("Missing required Twitch EventSub headers");
            return ResponseEntity.badRequest().body("Missing headers");
        }

        List<TwitchConfig> configs = twitchConfigRepository.findAll();
        if (configs.isEmpty()) {
            log.error("No Twitch configuration found to verify webhook");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        String secret = configs.get(0).getWebhookSecret();

        if (secret != null && !secret.isBlank()) {
            if (!verifySignature(secret, messageId, timestamp, body, signature)) {
                log.warn("Invalid Twitch Webhook signature for message ID: {}", messageId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
            }
        } else {
            log.warn("Webhook secret not configured, skipping signature verification (NOT RECOMMENDED for production)");
        }

        if ("webhook_callback_verification".equals(messageType)) {
            return handleVerification(body);
        } else if ("notification".equals(messageType)) {
            return handleNotification(body);
        } else if ("revocation".equals(messageType)) {
            log.warn("Webhook subscription revoked by Twitch: {}", body);
            return ResponseEntity.ok().build();
        }

        log.info("Received unhandled Twitch Webhook type: {}", messageType);
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<String> handleVerification(String body) {
        try {
            // Simplified challenge extraction from JSON
            String challenge = body.contains("\"challenge\":") 
                ? body.split("\"challenge\":")[1].split("\"")[1] 
                : null;
            
            if (challenge != null) {
                log.info("Responding to Twitch Webhook verification challenge");
                return ResponseEntity.ok(challenge);
            }
        } catch (Exception e) {
            log.error("Error parsing verification challenge: {}", e.getMessage());
        }
        return ResponseEntity.badRequest().build();
    }

    private ResponseEntity<String> handleNotification(String body) {
        TwitchClient client = twitchBotService.getTwitchClient();
        if (client == null) {
            log.error("TwitchClient not initialized, cannot process notification");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            // twitch4j provides a way to parse the notification
            EventSubNotification notification = objectMapper.readValue(body, EventSubNotification.class);
            if (notification != null && notification.getEvent() != null) {
                // Dispatch to twitch4j event manager
                client.getEventManager().publish(notification.getEvent());
                log.debug("Processed Twitch notification: {}", notification.getSubscription().getType());
            }
        } catch (Exception e) {
            log.error("Error processing Twitch notification: {}", e.getMessage(), e);
            // Return 200 anyway to stop Twitch from retrying if it's a permanent parsing error
        }

        return ResponseEntity.ok().build();
    }

    private boolean verifySignature(String secret, String messageId, String timestamp, String body, String signature) {
        try {
            String hmacMessage = messageId + timestamp + body;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(hmacMessage.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder sb = new StringBuilder("sha256=");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            String expectedSignature = sb.toString();
            return expectedSignature.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Error verifying webhook signature: {}", e.getMessage());
            return false;
        }
    }
}
