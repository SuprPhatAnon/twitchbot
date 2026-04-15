package dev.phatanon.dto;

import dev.phatanon.entity.TwitchConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TwitchConfigDTOTest {

    @Test
    void fromEntity_MasksSensitiveFields() {
        TwitchConfig entity = new TwitchConfig();
        entity.setClientId("id");
        entity.setClientSecret("secret");
        entity.setAccessToken("token");
        entity.setRefreshToken("refresh");
        entity.setBotAccessToken("bot-token");
        entity.setBotRefreshToken("bot-refresh");

        TwitchConfigDTO dto = TwitchConfigDTO.fromEntity(entity);

        assertEquals("id", dto.getClientId());
        assertEquals("********", dto.getClientSecret());
        assertEquals("********", dto.getAccessToken());
        assertEquals("********", dto.getRefreshToken());
        assertEquals("********", dto.getBotAccessToken());
        assertEquals("********", dto.getBotRefreshToken());
    }

    @Test
    void fromEntity_HandlesNullValues() {
        TwitchConfig entity = new TwitchConfig();
        TwitchConfigDTO dto = TwitchConfigDTO.fromEntity(entity);

        assertNull(dto.getClientId());
        assertNull(dto.getClientSecret());
        assertNull(dto.getAccessToken());
        assertNull(dto.getRefreshToken());
        assertNull(dto.getBotAccessToken());
        assertNull(dto.getBotRefreshToken());
    }
}
