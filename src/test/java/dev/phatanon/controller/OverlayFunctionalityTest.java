package dev.phatanon.controller;

import dev.phatanon.entity.Song;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OverlayFunctionalityTest extends BaseSeleniumTest {

    private Song testSong;

    @BeforeEach
    void setupData() {
        testSong = new Song("Test Song", "Test Artist", "/songs/test.mp3");
        testSong.setCoverArt("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        testSong.setEnabled(true);
        testSong = songRepository.save(testSong);
    }

    @AfterEach
    @Override
    void teardown() {
        super.teardown();
        // userRepository.deleteAll(); // Already handled in BeforeEach
    }

    private void waitForWebSocket() {
        new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> {
            try {
                Object result = ((JavascriptExecutor) d).executeScript(
                    "return typeof stompClient !== 'undefined' && stompClient !== null && stompClient.connected;"
                );
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                return false;
            }
        });
    }

    private void triggerSongViaApi(Long songId) {
        System.out.println("[DEBUG_LOG] Triggering song " + songId + " via API");
        
        // Use Javascript to open a new window to login and trigger the API
        // This keeps the overlay window active and connected
        String originalHandle = driver.getWindowHandle();
        ((JavascriptExecutor) driver).executeScript("window.open('about:blank', '_blank');");
        
        for (String handle : driver.getWindowHandles()) {
            if (!handle.equals(originalHandle)) {
                driver.switchTo().window(handle);
                break;
            }
        }
        
        try {
            login("admin", "admin");
            driver.get(getBaseUrl() + "/api/songs/play/" + songId);
            // wait a bit for the request to complete
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
        } finally {
            driver.close();
            driver.switchTo().window(originalHandle);
        }
    }

    @Test
    @DisplayName("Overlay should show song info when a song starts and clear when it finishes")
    void testOverlayBasicFlow() {
        driver.get(getBaseUrl() + "/overlay.html");
        
        // Wait for page to load
        assertTrue(driver.getTitle().contains("Twitch Song Overlay"));
        
        waitForWebSocket();
        
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        
        // 1. Simulate song playback by directly calling the JS function
        System.out.println("[DEBUG_LOG] Simulating song playback via JS");
        String songJson = "{\"id\":1, \"name\":\"Simulated Song\", \"artist\":\"Simulated Artist\", \"url\":\"/songs/test.mp3\", \"coverArt\":\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==\"}";
        // Ensure playSong exists and is ready
        wait.until(d -> (Boolean) ((JavascriptExecutor) d).executeScript("return typeof updateDisplay === 'function'"));
        ((JavascriptExecutor) driver).executeScript("console.log('[DEBUG_LOG] Manually calling updateDisplay'); updateDisplay(" + songJson + ");");
        
        // 2. Verify display appears
        System.out.println("[DEBUG_LOG] Waiting for song-display to become visible");
        wait.until(d -> {
            try {
                String titleText = (String) ((JavascriptExecutor) d).executeScript("return document.getElementById('song-title').innerText;");
                String displayStyle = (String) ((JavascriptExecutor) d).executeScript("return window.getComputedStyle(document.getElementById('song-display')).display;");
                System.out.println("[DEBUG_LOG] Basic Flow Check - Title: '" + titleText + "', Style: " + displayStyle);
                return titleText != null && !titleText.isEmpty() && !"none".equals(displayStyle);
            } catch (Exception e) {
                return false;
            }
        });
        
        String songTitleText = (String) ((JavascriptExecutor) driver).executeScript("return document.getElementById('song-title').innerText;");
        assertEquals("Simulated Song - Simulated Artist", songTitleText);
        
        // 3. Simulate song finishing (audio 'onended' event or calling clearDisplay/notifySongFinished)
        ((JavascriptExecutor) driver).executeScript("clearDisplay(); notifySongFinished();");
        
        // 4. Verify display is cleared
        wait.until(d -> {
            String displayStyle = (String) ((JavascriptExecutor) d).executeScript("return window.getComputedStyle(document.getElementById('song-display')).display;");
            return "none".equals(displayStyle);
        });
    }

    @Test
    @DisplayName("Overlay should sync state if loaded while a song is already playing")
    void testOverlayStateSync() {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        
        // 1. Start song BEFORE loading overlay
        twitchBotService.playSongById(testSong.getId());
        
        // Wait for song to be "playing" in service
        wait.until(d -> twitchBotService.isSongPlaying());
        
        // 2. Load overlay
        driver.get(getBaseUrl() + "/overlay.html");
        
        waitForWebSocket();
        
        // Explicitly request state just in case the automatic one was missed or too early
        ((JavascriptExecutor) driver).executeScript("stompClient.send('/app/request-state', {}, {});");

        // 3. Verify it shows the song info immediately (or after WS connect and state request)
        // We use a custom wait that returns the text when it's found to avoid race conditions
        // where it might be present momentarily and then cleared due to audio error in headless chrome.
        String songTitleText = wait.until(d -> {
            try {
                String titleText = (String) ((JavascriptExecutor) driver).executeScript("return document.getElementById('song-title').innerText;");
                if (titleText != null && titleText.contains("Test Song")) {
                    return titleText;
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        });
        
        assertEquals("Test Song - Test Artist", songTitleText);
    }

    @Test
    @DisplayName("Overlay should clear display when queue is cleared")
    void testOverlayQueueClear() {
        driver.get(getBaseUrl() + "/overlay.html");
        
        waitForWebSocket();
        
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        
        // 1. Start song
        twitchBotService.playSongById(testSong.getId());
        wait.until(d -> {
            try {
                String titleText = (String) ((JavascriptExecutor) d).executeScript("return document.getElementById('song-title').innerText;");
                return titleText.contains("Test Song");
            } catch (Exception e) {
                return false;
            }
        });
        
        // 2. Clear queue
        twitchBotService.clearQueue();
        
        // 3. Verify display is hidden
        wait.until(d -> {
            try {
                String displayStyle = (String) ((JavascriptExecutor) d).executeScript("return window.getComputedStyle(document.getElementById('song-display')).display;");
                return "none".equals(displayStyle);
            } catch (Exception e) {
                return false;
            }
        });
    }
    
    @Test
    @DisplayName("Overlay should handle song without cover art")
    void testOverlayNoCoverArt() {
        Song noArtSong = new Song("No Art", "No Artist", "/songs/noart.mp3");
        noArtSong.setEnabled(true);
        noArtSong = songRepository.save(noArtSong);
        
        driver.get(getBaseUrl() + "/overlay.html");
        
        waitForWebSocket();
        
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        
        twitchBotService.playSongById(noArtSong.getId());
        
        wait.until(d -> {
            try {
                String titleText = (String) ((JavascriptExecutor) d).executeScript("return document.getElementById('song-title').innerText;");
                return titleText.contains("No Art");
            } catch (Exception e) {
                return false;
            }
        });
        
        String artDisplay = (String) ((JavascriptExecutor) driver).executeScript("return window.getComputedStyle(document.getElementById('song-art')).display;");
        assertEquals("none", artDisplay, "Song art should NOT be visible for song without cover art");
    }
}
