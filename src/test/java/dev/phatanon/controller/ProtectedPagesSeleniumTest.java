package dev.phatanon.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtectedPagesSeleniumTest extends BaseSeleniumTest {

    @Test
    @DisplayName("Admin page should redirect to login if not authenticated")
    void testAdminPageRedirect() {
        driver.get(getBaseUrl() + "/admin.html");
        assertTrue(driver.getCurrentUrl().contains("login.html"), "Should be redirected to login page");
    }

    @Test
    @DisplayName("Admin page should be accessible after login")
    void testAdminPageAccess() {
        login("admin", "admin");
        driver.get(getBaseUrl() + "/admin.html");
        assertTrue(driver.getTitle().contains("Twitch Bot Admin UI"), "Title should contain 'Twitch Bot Admin UI'");
    }

    @Test
    @DisplayName("Streamer page should be accessible after login")
    void testStreamerPageAccess() {
        login("admin", "admin");
        driver.get(getBaseUrl() + "/streamer.html");
        assertTrue(driver.getTitle().contains("Twitch Bot Streamer UI"), "Title should contain 'Twitch Bot Streamer UI'");
    }

    @Test
    @DisplayName("Upload page should be accessible after login")
    void testUploadPageAccess() {
        login("admin", "admin");
        driver.get(getBaseUrl() + "/upload.html");
        assertTrue(driver.getTitle().contains("Twitch Song Bot - Upload Song"), "Title should contain 'Twitch Song Bot - Upload Song'");
    }

    @Test
    @DisplayName("Song Management page should be accessible after login")
    void testSongManagementPageAccess() {
        login("admin", "admin");
        driver.get(getBaseUrl() + "/song-management.html");
        assertTrue(driver.getTitle().contains("Song File Management - Twitch Bot"), "Title should contain 'Song File Management - Twitch Bot'");
    }

    @Test
    @DisplayName("User Management page should be accessible after login")
    void testUserManagementPageAccess() {
        login("admin", "admin");
        driver.get(getBaseUrl() + "/user-management.html");
        assertTrue(driver.getTitle().contains("User Management - Twitch Song Overlay"), "Title should contain 'User Management - Twitch Song Overlay'");
    }

    @Test
    @DisplayName("Account page should be accessible after login")
    void testAccountPageAccess() {
        login("admin", "admin");
        driver.get(getBaseUrl() + "/account.html");
        assertTrue(driver.getTitle().contains("Account Info - Twitch Song Overlay"), "Title should contain 'Account Info - Twitch Song Overlay'");
    }

    @Test
    @DisplayName("Status page should be accessible after login")
    void testStatusPageAccess() {
        login("admin", "admin");
        driver.get(getBaseUrl() + "/status.html");
        assertTrue(driver.getTitle().contains("Twitch Bot Status"), "Title should contain 'Twitch Bot Status'");
    }
}
