package dev.phatanon.controller;

import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class LoginSeleniumTest extends BaseSeleniumTest {

    @Test
    @DisplayName("Login page should display error message on failed login")
    void testLoginError() {
        driver.get(getBaseUrl() + "/login.html?error");
        
        WebElement errorMessage = driver.findElement(By.id("errorMessage"));
        assertTrue(errorMessage.isDisplayed(), "Error message should be visible when 'error' param is present");
    }
}
