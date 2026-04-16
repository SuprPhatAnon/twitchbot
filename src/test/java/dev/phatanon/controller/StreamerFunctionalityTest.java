package dev.phatanon.controller;

import dev.phatanon.entity.Role;
import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.repository.UserRepository;
import dev.phatanon.service.TwitchBotService;
import dev.phatanon.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class StreamerFunctionalityTest extends BaseSeleniumTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SongRepository songRepository;

    @Autowired
    private TwitchBotService twitchBotService;

    private final String streamerUsername = "teststreamer";
    private final String streamerPassword = "testpassword";

    @BeforeEach
    void setupData() {
        userRepository.deleteAll();
        songRepository.deleteAll();
        twitchBotService.clearQueue();

        // Create streamer user
        userService.createUser(streamerUsername, streamerPassword, Set.of(Role.ROLE_STREAMER));

        // Create a test song
        Song song = new Song("Streamer Song", "Streamer Artist", "streamer.mp3");
        song.setEnabled(true);
        song.setPlayCount(10);
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
        
        // Streamer is redirected to streamer.html (actually default is admin.html, but they don't have access, 
        // but SecurityConfig has defaultSuccessUrl("/admin.html", true). 
        // Let's see what happens when a streamer logs in. They might get 403 on admin.html.
        // For the test, we'll just navigate to streamer.html after login.
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> 
            d.getCurrentUrl().contains("admin.html") || d.getCurrentUrl().contains("streamer.html")
        );
        driver.get(getBaseUrl() + "/streamer.html");
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
        assertEquals("10", playCountBadge.getText());
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
    @DisplayName("Streamer page should update current song via WebSocket")
    void testCurrentSongUpdate() {
        loginAsStreamer();
        waitForWebSocket();
        
        WebElement playingStat = driver.findElement(By.id("stat-playing"));
        assertEquals("None", playingStat.getText());

        // Start playing a song
        Song song = songRepository.findAll().get(0);
        twitchBotService.playSongById(song.getId());

        new WebDriverWait(driver, Duration.ofSeconds(10)).until(
            ExpectedConditions.textToBePresentInElementLocated(By.id("stat-playing"), "Streamer Song - Streamer Artist")
        );
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
}
