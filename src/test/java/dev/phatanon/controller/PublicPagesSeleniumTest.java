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
    @Test
    @DisplayName("Player page should have a volume control that persists in localStorage")
    void testPlayerVolumeControl() {
        driver.get(getBaseUrl() + "/player.html");
        
        // Wait for player-volume to be present
        org.openqa.selenium.support.ui.WebDriverWait wait = new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(10));
        org.openqa.selenium.WebElement volumeInput = wait.until(org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(By.id("player-volume")));
        
        // Change volume via JS to 0.25
        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].value = 0.25; arguments[0].dispatchEvent(new Event('input'))", volumeInput);
        
        // Verify audio element volume
        Double audioVolume = (Double) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("return document.getElementById('audio-player').volume");
        org.junit.jupiter.api.Assertions.assertEquals(0.25, audioVolume, 0.01);
        
        // Verify localStorage
        String storedVolume = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("return localStorage.getItem('player-volume')");
        org.junit.jupiter.api.Assertions.assertEquals("0.25", storedVolume);
        
        // Refresh page and check if it persists
        driver.navigate().refresh();
        volumeInput = wait.until(org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(By.id("player-volume")));
        org.junit.jupiter.api.Assertions.assertEquals("0.25", volumeInput.getAttribute("value"));
        audioVolume = (Double) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("return document.getElementById('audio-player').volume");
        org.junit.jupiter.api.Assertions.assertEquals(0.25, audioVolume, 0.01);
    }
}
