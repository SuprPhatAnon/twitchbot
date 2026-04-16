package dev.phatanon.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PublicPagesSeleniumTest extends BaseSeleniumTest {

    @Test
    @DisplayName("Overlay page should load and have correct title")
    void testOverlayPage() {
        driver.get(getBaseUrl() + "/overlay.html");
        assertTrue(driver.getTitle().contains("Twitch Song Overlay"), "Title should contain 'Twitch Song Overlay'");
    }

    @Test
    @DisplayName("Player page should load and have correct title")
    void testPlayerPage() {
        driver.get(getBaseUrl() + "/player.html");
        assertTrue(driver.getTitle().contains("Twitch Bot - Music Player"), "Title should contain 'Twitch Bot - Music Player'");
    }

    @Test
    @DisplayName("Statistics page should load and have correct title")
    void testStatisticsPage() {
        driver.get(getBaseUrl() + "/statistics.html");
        assertTrue(driver.getTitle().contains("Song Play Statistics"), "Title should contain 'Song Play Statistics'");
    }

    @Test
    @DisplayName("Login page should load and have login form")
    void testLoginPage() {
        driver.get(getBaseUrl() + "/login.html");
        assertTrue(driver.findElement(By.id("loginForm")).isDisplayed(), "Login form should be visible");
    }
}
