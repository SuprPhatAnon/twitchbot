package dev.phatanon.controller;

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

@RestController
@RequestMapping("/api/qa")
@Profile("test")
public class QaController {

    @Value("${twitch.local-cli-url:http://twitch-cli:8080}")
    private String localCliUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/trigger")
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
