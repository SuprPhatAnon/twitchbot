package dev.phatanon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestRedisConfiguration.class)
class TwitchBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
