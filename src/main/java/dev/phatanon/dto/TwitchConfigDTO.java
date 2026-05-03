package dev.phatanon.dto;

import dev.phatanon.entity.TwitchConfig;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Twitch configuration, masking sensitive data.
 */
@Data
@NoArgsConstructor
public class TwitchConfigDTO {
    private Long id;
    private String clientId;
    private String clientSecret;
    private String accessToken;
    
    private String refreshToken;
    
    private String botAccessToken;
    
    private String botRefreshToken;
    
    private String webhookSecret;
    
    private String channelName;
    private int songDelaySeconds;

    public static TwitchConfigDTO fromEntity(TwitchConfig entity) {
        TwitchConfigDTO dto = new TwitchConfigDTO();
        dto.setId(entity.getId());
        dto.setClientId(entity.getClientId());
        dto.setClientSecret(mask(entity.getClientSecret()));
        dto.setAccessToken(mask(entity.getAccessToken()));
        dto.setRefreshToken(mask(entity.getRefreshToken()));
        dto.setBotAccessToken(mask(entity.getBotAccessToken()));
        dto.setBotRefreshToken(mask(entity.getBotRefreshToken()));
        dto.setWebhookSecret(mask(entity.getWebhookSecret()));
        dto.setChannelName(entity.getChannelName());
        dto.setSongDelaySeconds(entity.getSongDelaySeconds());
        return dto;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        return "********";
    }
}
