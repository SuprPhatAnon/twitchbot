package dev.phatanon.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AdminFunctionalityTest extends BaseSeleniumTest {

    @BeforeEach
    void setupAdminUser() {
        // Ensure at least one song exists for filtering test
        if (songRepository.count() == 0) {
            dev.phatanon.entity.Song song = new dev.phatanon.entity.Song("Test Song", "Test Artist", "test.mp3");
            song.setEnabled(true);
            songRepository.save(song);
        }
        
        login("admin", "admin");
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

    @Test
    @DisplayName("Admin page 'Play Random' button should add a random song to the queue")
    void testPlayRandomSong() {
        driver.get(getBaseUrl() + "/admin.html");
        waitForWebSocket();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Wait for status bar to be visible
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("stat-queue")));
        
        // Initial queue should be 0 (resetDatabase is called in BaseSeleniumTest @BeforeEach)
        assertEquals("0", driver.findElement(By.id("stat-queue")).getText());

        // Click "Play Random" button
        WebElement playRandomBtn = driver.findElement(By.xpath("//button[text()='Play Random']"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", playRandomBtn);

        // Wait for queue to become 1 OR playing to become something else
        wait.until(d -> {
            String q = d.findElement(By.id("stat-queue")).getText();
            String p = d.findElement(By.id("stat-playing")).getText();
            return !q.equals("0") || !p.equals("None");
        });
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
        try {
            deleteBtn.click();
        } catch (org.openqa.selenium.ElementClickInterceptedException e) {
            // If something is covering it, try JS click
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", deleteBtn);
        }
        
        wait.until(ExpectedConditions.alertIsPresent());
        driver.switchTo().alert().accept();

        // Verify it's gone
        wait.until(ExpectedConditions.not(ExpectedConditions.textToBePresentInElementLocated(By.id("redeems-list"), newRedeemTitle)));
    }

    @Test
    @DisplayName("Admin page should filter songs correctly")
    void testSongFiltering() {
        driver.get(getBaseUrl() + "/admin.html");
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // Wait for table to load
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("songs-table-body")));
        
        // Wait for "Test Song" to be rendered (ensure setupAdminUser worked)
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.id("songs-table-body"), "Test Song"));
        
        // Count initial rows
        int initialRowCount = driver.findElements(By.cssSelector("#songs-table-body tr")).size();

        WebElement searchInput = driver.findElement(By.id("song-search"));
        
        // Wait for any previous animations or rendering to settle
        wait.until(ExpectedConditions.elementToBeClickable(searchInput));

        // Use a term that likely won't match anything initially to test filtering
        String nonExistentTerm = "NonExistentSongXYZ";
        searchInput.sendKeys(nonExistentTerm);

        // Wait for table to be empty
        wait.until(d -> {
            List<WebElement> rows = d.findElements(By.cssSelector("#songs-table-body tr"));
            System.out.println("[DEBUG_LOG] Filtering Check - Rows found after typing non-existent term: " + rows.size());
            return rows.isEmpty();
        });

        // Clear search
        searchInput.clear();
        // Force clear and trigger input event
        ((JavascriptExecutor) driver).executeScript("arguments[0].value = ''; arguments[0].dispatchEvent(new Event('input'));", searchInput);

        // Wait for table to restore
        wait.until(d -> d.findElements(By.cssSelector("#songs-table-body tr")).size() >= initialRowCount);

        searchInput.sendKeys("Test Song");
        
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
