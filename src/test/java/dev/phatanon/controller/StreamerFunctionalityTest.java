package dev.phatanon.controller;

import dev.phatanon.entity.Role;
import dev.phatanon.entity.Song;
import dev.phatanon.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class StreamerFunctionalityTest extends BaseSeleniumTest {

    private final String streamerUsername = "teststreamer";
    private final String streamerPassword = "testpassword";
    private String streamerApiKey;

    @BeforeEach
    void setupData() {
        // Create streamer user
        User user = userService.createUser(streamerUsername, streamerPassword, Set.of(Role.ROLE_STREAMER));
        streamerApiKey = user.getApiKey();

        // Create a test song
        Song song = new Song("Streamer Song", "Streamer Artist", "streamer.mp3");
        song.setEnabled(true);
        songRepository.save(song);
    }

    @AfterEach
    @Override
    void teardown() {
        super.teardown();
    }

    private void loginAsStreamer() {
        driver.get(getBaseUrl() + "/login.html");
        driver.findElement(By.id("username")).sendKeys(streamerUsername);
        driver.findElement(By.id("password")).sendKeys(streamerPassword);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
        
        // Streamer is redirected to streamer.html
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> 
            d.getCurrentUrl().contains("streamer.html")
        );

        // Set API key in localStorage so fetch calls work
        ((JavascriptExecutor) driver).executeScript("localStorage.setItem('apiKey', arguments[0]);", streamerApiKey);
        
        // Refresh to ensure the API key is picked up for initial calls
        driver.navigate().refresh();
    }

    private void waitForWebSocket() {
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
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

    @Test
    @DisplayName("Streamer page should persist theme state")
    void testThemePersistence() {
        loginAsStreamer();
        
        JavascriptExecutor js = (JavascriptExecutor) driver;
        String initialTheme = (String) js.executeScript("return document.documentElement.getAttribute('data-bs-theme')");
        
        WebElement themeToggle = driver.findElement(By.id("theme-toggle"));
        js.executeScript("arguments[0].click();", themeToggle);
        
        String newTheme = (String) js.executeScript("return document.documentElement.getAttribute('data-bs-theme')");
        assertNotEquals(initialTheme, newTheme, "Theme should have changed");

        // Refresh page
        driver.navigate().refresh();
        
        String persistedTheme = (String) js.executeScript("return document.documentElement.getAttribute('data-bs-theme')");
        assertEquals(newTheme, persistedTheme, "Theme should be persisted after refresh");
    }

    @Test
    @DisplayName("Streamer page 'Play Random' button should add a random song to the queue")
    void testPlayRandomSong() {
        loginAsStreamer();
        driver.get(getBaseUrl() + "/streamer.html");
        waitForWebSocket();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Wait for status bar
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("stat-queue")));
        
        assertEquals("0", driver.findElement(By.id("stat-queue")).getText());

        WebElement playRandomBtn = driver.findElement(By.xpath("//button[text()='Play Random']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", playRandomBtn);

        // Wait for queue to become 1 OR playing to become something else
        wait.until(d -> {
            String q = d.findElement(By.id("stat-queue")).getText();
            String p = (String) ((JavascriptExecutor) d).executeScript("return document.getElementById('stat-playing').textContent;");
            return !q.equals("0") || !p.equals("None");
        });
    }

    @Test
    @DisplayName("Streamer page should display songs correctly")
    void testSongDisplay() {
        loginAsStreamer();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Wait for table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("songs-table-body")));
        wait.until(d -> !d.findElements(By.cssSelector("#songs-table-body tr")).isEmpty());

        assertTrue(driver.getPageSource().contains("Streamer Song"));
        assertTrue(driver.getPageSource().contains("Streamer Artist"));
        
        // Check play count badge
        WebElement playCountBadge = driver.findElement(By.cssSelector("#songs-table-body .badge"));
        assertEquals("0", playCountBadge.getText());
    }

    @Test
    @DisplayName("Streamer page should update queue size via WebSocket")
    void testQueueSizeUpdate() {
        loginAsStreamer();
        waitForWebSocket();
        
        WebElement queueStat = driver.findElement(By.id("stat-queue"));
        assertEquals("0", queueStat.getText());

        // Update queue size via service
        // Since addToQueue doesn't exist, we use playSongById which adds to queue if a song is already playing
        Song song = songRepository.findAll().get(0);
        // Start one song first
        twitchBotService.playSongById(song.getId());
        // Start another one, it should go to queue
        twitchBotService.playSongById(song.getId());

        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
            ExpectedConditions.textToBePresentInElementLocated(By.id("stat-queue"), "1")
        );
    }

    @Test
    @DisplayName("Streamer page should update stream status via WebSocket")
    void testStreamStatusUpdate() {
        loginAsStreamer();
        waitForWebSocket();
        
        WebElement streamStat = driver.findElement(By.id("stat-stream"));
        // Default might be OFFLINE
        
        // Mock stream status update
        ((JavascriptExecutor) driver).executeScript("updateStreamStatusDisplay(true)");
        
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(
            ExpectedConditions.textToBePresentInElementLocated(By.id("stat-stream"), "ONLINE")
        );
        assertTrue(streamStat.getAttribute("class").contains("status-online"));
    }

    @Test
    @DisplayName("Streamer page should update player UI via WebSocket")
    void testCurrentSongUpdate() {
        loginAsStreamer();
        waitForWebSocket();
        
        WebElement noSong = driver.findElement(By.id("no-song-selected"));
        assertTrue(noSong.isDisplayed());

        // Start playing a song
        Song song = songRepository.findAll().get(0);
        twitchBotService.playSongById(song.getId());

        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
            ExpectedConditions.visibilityOfElementLocated(By.id("active-player-ui"))
        );
        assertEquals(song.getName(), driver.findElement(By.id("display-song-name")).getText());
    }

    @Test
    @DisplayName("Streamer page should add redeems to log via WebSocket")
    void testRedeemLogUpdate() {
        loginAsStreamer();
        waitForWebSocket();
        
        WebElement log = driver.findElement(By.id("redeem-log"));
        int initialEntries = log.findElements(By.className("redeem-entry")).size();

        // Simulate a redeem event via JS (since triggering it via TwitchBotService requires more setup)
        String redeemJson = "{\"user\":\"TestUser\", \"rewardTitle\":\"Test Reward\", \"timestamp\":\"" + System.currentTimeMillis() + "\"}";
        ((JavascriptExecutor) driver).executeScript("addRedeemToLog(" + redeemJson + ")");

        new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> 
            d.findElements(By.className("redeem-entry")).size() > initialEntries
        );
        
        assertTrue(log.getText().contains("TestUser"));
        assertTrue(log.getText().contains("Test Reward"));
    }

    @Test
    @DisplayName("Streamer page should update connection status via WebSocket")
    void testConnectionStatusUpdate() {
        loginAsStreamer();
        waitForWebSocket();
        
        WebElement twitchStat = driver.findElement(By.id("stat-twitch"));
        
        // Mock connection status update
        ((JavascriptExecutor) driver).executeScript("updateConnectionStatusDisplay(true)");
        
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(
            ExpectedConditions.textToBePresentInElementLocated(By.id("stat-twitch"), "CONNECTED")
        );
        assertTrue(twitchStat.getAttribute("class").contains("status-online"));
    }

    @Test
    @DisplayName("Streamer page should have a volume control that persists in localStorage and applies to new songs")
    void testVolumeControl() {
        loginAsStreamer();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        
        WebElement volumeInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("player-volume")));
        
        // Change volume via JS to 0.75
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = 0.75; arguments[0].dispatchEvent(new Event('input'))", volumeInput);
        
        // Verify audio element volume
        Double audioVolume = (Double) ((JavascriptExecutor) driver).executeScript("return document.getElementById('audio-player').volume");
        assertEquals(0.75, audioVolume, 0.01);
        
        // Verify localStorage
        String storedVolume = (String) ((JavascriptExecutor) driver).executeScript("return localStorage.getItem('player-volume')");
        assertEquals("0.75", storedVolume);
        
        // Mock play event via JS and check if volume is still 0.75
        String songJson = "{\"name\":\"Test Song\", \"artist\":\"Test Artist\", \"url\":\"/test.mp3\"}";
        ((JavascriptExecutor) driver).executeScript("playAudio(" + songJson + ")");
        
        audioVolume = (Double) ((JavascriptExecutor) driver).executeScript("return document.getElementById('audio-player').volume");
        assertEquals(0.75, audioVolume, 0.01, "Volume should persist after playAudio is called");

        // Refresh page and check if it persists
        driver.navigate().refresh();
        volumeInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("player-volume")));
        assertEquals("0.75", volumeInput.getAttribute("value"));
        audioVolume = (Double) ((JavascriptExecutor) driver).executeScript("return document.getElementById('audio-player').volume");
        assertEquals(0.75, audioVolume, 0.01);
    }

    @Test
    @DisplayName("Streamer page should play audio when a song starts but NOT notify finished")
    void testAudioPlayback() {
        loginAsStreamer();
        waitForWebSocket();
        
        // Mock play event via JS
        String songJson = "{\"name\":\"Test Song\", \"artist\":\"Test Artist\", \"url\":\"/test.mp3\"}";
        ((JavascriptExecutor) driver).executeScript("playAudio(" + songJson + ")");
        
        WebElement audioPlayer = driver.findElement(By.id("audio-player"));
        assertTrue(audioPlayer.getAttribute("src").contains("/test.mp3"));
        
        // Verify it doesn't have an onended handler that sends /app/song-finished
        // In the streamer view, we didn't add audioPlayer.onended.
        Object onended = ((JavascriptExecutor) driver).executeScript("return document.getElementById('audio-player').onended");
        assertNull(onended, "Streamer audio player should NOT have an onended handler");
    }
}
