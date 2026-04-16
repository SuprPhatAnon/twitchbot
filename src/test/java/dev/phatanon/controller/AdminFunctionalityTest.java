package dev.phatanon.controller;

import dev.phatanon.entity.Role;
import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.service.UserService;
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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class AdminFunctionalityTest extends BaseSeleniumTest {

    @Autowired
    private UserService userService;

    @Autowired
    private SongRepository songRepository;

    @BeforeEach
    void setupAdminUser() {
        if (!userService.existsByUsername("admin")) {
            userService.createUser("admin", "admin", Set.of(Role.ROLE_ADMIN));
        }
        
        // Ensure at least one song exists for filtering test
        if (songRepository.count() == 0) {
            Song song = new Song("Test Song", "Test Artist", "test.mp3");
            song.setEnabled(true);
            songRepository.save(song);
        }
        
        login("admin", "admin");
    }

    @Test
    @DisplayName("Admin page should allow adding and deleting redeems")
    void testManageRedeems() {
        driver.get(getBaseUrl() + "/admin.html");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Wait for redeems list to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("redeems-list")));

        String newRedeemTitle = "Test Redeem " + System.currentTimeMillis();
        
        // Add redeem
        WebElement input = driver.findElement(By.id("new-redeem-title"));
        input.sendKeys(newRedeemTitle);
        driver.findElement(By.cssSelector("#add-redeem-form button")).click();

        // Verify it appears in the list
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("redeems-list"), newRedeemTitle));
        
        // Find the badge and delete button
        WebElement list = driver.findElement(By.id("redeems-list"));
        wait.until(d -> list.findElements(By.className("badge")).stream()
                .anyMatch(b -> b.getText().contains(newRedeemTitle)));
        
        List<WebElement> badges = list.findElements(By.className("badge"));
        WebElement targetBadge = badges.stream()
                .filter(b -> b.getText().contains(newRedeemTitle))
                .findFirst()
                .orElseThrow();
        
        WebElement deleteBtn = targetBadge.findElement(By.className("btn-close"));
        
        // Click delete and handle confirmation alert
        deleteBtn.click();
        wait.until(ExpectedConditions.alertIsPresent());
        driver.switchTo().alert().accept();

        // Verify it's gone
        wait.until(ExpectedConditions.not(ExpectedConditions.textToBePresentInElementLocated(By.id("redeems-list"), newRedeemTitle)));
    }

    @Test
    @DisplayName("Admin page should filter songs correctly")
    void testSongFiltering() {
        driver.get(getBaseUrl() + "/admin.html");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Wait for table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("songs-table-body")));
        // Wait for at least one row to be rendered (async)
        wait.until(d -> !d.findElements(By.cssSelector("#songs-table-body tr")).isEmpty());

        WebElement searchInput = driver.findElement(By.id("song-search"));
        
        // Use a term that likely won't match anything initially to test filtering
        String nonExistentTerm = "NonExistentSongXYZ";
        searchInput.sendKeys(nonExistentTerm);

        // Wait for table to be empty (or contain the "No songs found" message if applicable, but here it just filters)
        wait.until(d -> {
            List<WebElement> rows = d.findElements(By.cssSelector("#songs-table-body tr"));
            // In admin.html, renderSongsTable() clears innerHTML and adds rows.
            // If filtered, rows might be empty.
            return rows.isEmpty();
        });

        // Clear search
        searchInput.clear();
        searchInput.sendKeys("Test Song"); // Use the one we created in setup
        
        wait.until(d -> !d.findElements(By.cssSelector("#songs-table-body tr")).isEmpty());
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("songs-table-body"), "Test Song"));
    }

    @Test
    @DisplayName("Admin page should persist card collapse state")
    void testCardCollapsePersistence() {
        driver.get(getBaseUrl() + "/admin.html");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement cardRedeems = driver.findElement(By.id("card-redeems"));
        WebElement header = cardRedeems.findElement(By.className("card-header"));
        WebElement body = cardRedeems.findElement(By.className("card-body"));

        // Initially shown
        assertTrue(body.getAttribute("class").contains("show"), "Card body should be visible initially");

        // Collapse it
        header.click();
        wait.until(ExpectedConditions.not(ExpectedConditions.attributeContains(body, "class", "show")));

        // Refresh page
        driver.navigate().refresh();
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("card-redeems")));
        
        cardRedeems = driver.findElement(By.id("card-redeems"));
        body = cardRedeems.findElement(By.className("card-body"));
        header = cardRedeems.findElement(By.className("card-header"));
        
        // Should still be collapsed
        assertFalse(body.getAttribute("class").contains("show"), "Card body should remain collapsed after refresh");

        // Expand it back
        header.click();
        wait.until(ExpectedConditions.attributeContains(body, "class", "show"));
    }

    @Test
    @DisplayName("Admin page should persist theme state")
    void testThemePersistence() {
        driver.get(getBaseUrl() + "/admin.html");
        
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
    @DisplayName("Admin page should display Twitch config status correctly")
    void testTwitchConfigUI() {
        driver.get(getBaseUrl() + "/admin.html");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Check if config indicators are present
        assertNotNull(driver.findElement(By.id("status-client-id")));
        assertNotNull(driver.findElement(By.id("status-client-secret")));
        
        // Test OAuth Helper URL generation (UI side)
        WebElement clientIdInput = driver.findElement(By.id("twitch-client-id"));
        clientIdInput.clear();
        clientIdInput.sendKeys("mock-client-id");
        
        WebElement redirectUriInput = driver.findElement(By.id("oauth-redirect-uri"));
        redirectUriInput.clear();
        redirectUriInput.sendKeys("http://localhost/callback");
        
        // Find and click "Streamer Auth URL (Legacy)" button
        WebElement generateBtn = driver.findElement(By.xpath("//button[contains(text(), 'Streamer Auth URL (Legacy)')]"));
        generateBtn.click();
        
        // Check if link is generated
        WebElement authLink = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("auth-url-link")));
        String href = authLink.getAttribute("href");
        assertTrue(href.contains("client_id=mock-client-id"), "Generated URL should contain client_id");
        assertTrue(href.contains("state=streamer"), "Generated URL should contain state=streamer");
    }
}
