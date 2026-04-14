package dev.phatanon.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.HashMap;

/**
 * REST controller for triggering mock Twitch events.
 * This controller is only active when the "test" profile is enabled.
 * It acts as a bridge to the qa-trigger-server.
 */
@RestController
@RequestMapping("/api/qa")
@Profile("test")
@Tag(name = "QA Endpoints", description = "Endpoints for triggering mock Twitch events (only available in 'test' profile)")
public class QaController {

    @Value("${twitch.local-cli-url:http://twitch-cli:8080}")
    private String localCliUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Triggers a mock Twitch event (e.g., redemption, subscription) via the qa-trigger-server.
     * @param event The name of the event to trigger.
     * @param allParams All other query parameters to be passed to the event trigger.
     * @return A {@link ResponseEntity} containing the result from the trigger server.
     */
    @GetMapping("/trigger")
    @Operation(summary = "Trigger a mock Twitch event")
    public ResponseEntity<String> triggerEvent(@RequestParam String event, @RequestParam Map<String, String> allParams) {
        // Construct the URL to the qa-trigger-server (port 8082)
        String triggerUrl = localCliUrl.replace(":8080", ":8082") + "/trigger?event=" + event;
        
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (!entry.getKey().equals("event")) {
                triggerUrl += "&" + entry.getKey() + "=" + entry.getValue();
            }
        }

        try {
            String result = restTemplate.getForObject(triggerUrl, String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error triggering event: " + e.getMessage());
        }
    }
}
