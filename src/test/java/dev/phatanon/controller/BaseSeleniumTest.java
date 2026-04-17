package dev.phatanon.controller;

import dev.phatanon.entity.Role;
import dev.phatanon.repository.*;
import dev.phatanon.service.TwitchBotService;
import dev.phatanon.service.UserService;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseSeleniumTest {

    @LocalServerPort
    protected int port;

    protected WebDriver driver;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected SongRepository songRepository;

    @Autowired
    protected RedeemRepository redeemRepository;

    @Autowired
    protected SongPlayRepository songPlayRepository;

    @Autowired
    protected TwitchConfigRepository twitchConfigRepository;

    @Autowired
    protected UserService userService;

    @Autowired
    protected TwitchBotService twitchBotService;

    /**
     * The Chrome version must match the selenium-devtools version in pom.xml.
     */
    protected static final String CHROME_VERSION = "146.0.7680.177";

    @BeforeAll
    static void setupClass() {
        WebDriverManager.chromedriver().driverVersion(CHROME_VERSION).setup();
    }

    @BeforeEach
    void setupTest() {
        resetDatabase();
        
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(1920, 1080));
    }

    /**
     * Resets the database and in-memory state to ensure a clean slate for each test.
     * Order of deletion is important to respect foreign key constraints:
     * 1. song_plays (refers to songs)
     * 2. songs (refers to redeems via join table)
     * 3. redeems
     * 4. twitch_config
     * 5. users (and roles via collection table)
     */
    protected void resetDatabase() {
        // Clear in-memory state first
        twitchBotService.clearQueue();

        // Clear database in order
        songPlayRepository.deleteAll();
        songRepository.deleteAll(); // This will clear song_redeem_link join table
        redeemRepository.deleteAll();
        twitchConfigRepository.deleteAll();
        userRepository.deleteAll(); // This will clear user_roles collection table
        
        // Recreate default admin user for tests
        userService.createUser("admin", "admin", Set.of(Role.ROLE_ADMIN));
    }

    @AfterEach
    void teardown() {
        if (driver != null) {
            driver.quit();
        }
    }

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    protected void login(String username, String password) {
        driver.get(getBaseUrl() + "/login.html");
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
        
        // Wait for login to complete and redirect to happen
        new org.openqa.selenium.support.ui.WebDriverWait(driver, Duration.ofSeconds(5))
            .until(org.openqa.selenium.support.ui.ExpectedConditions.urlContains("admin.html"));
    }
}
