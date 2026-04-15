package dev.phatanon.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwitchConfigTest {

    @Test
    void testGettersAndSetters() {
        TwitchConfig config = new TwitchConfig();
        
        config.setId(1L);
        config.setClientId("clientId");
        config.setClientSecret("clientSecret");
        config.setAccessToken("accessToken");
        config.setRefreshToken("refreshToken");
        config.setBotAccessToken("botAccessToken");
        config.setBotRefreshToken("botRefreshToken");
        config.setChannelName("channelName");
        config.setSongDelaySeconds(10);

        assertEquals(1L, config.getId());
        assertEquals("clientId", config.getClientId());
        assertEquals("clientSecret", config.getClientSecret());
        assertEquals("accessToken", config.getAccessToken());
        assertEquals("refreshToken", config.getRefreshToken());
        assertEquals("botAccessToken", config.getBotAccessToken());
        assertEquals("botRefreshToken", config.getBotRefreshToken());
        assertEquals("channelName", config.getChannelName());
        assertEquals(10, config.getSongDelaySeconds());
    }
}
