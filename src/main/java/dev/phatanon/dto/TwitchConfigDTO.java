package dev.phatanon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.phatanon.entity.TwitchConfig;

/**
 * Data Transfer Object for Twitch configuration, masking sensitive data.
 */
public class TwitchConfigDTO {
    private Long id;
    private String clientId;
    
    private String clientSecret;
    
    private String accessToken;
    
    private String refreshToken;
    
    private String botAccessToken;
    
    private String botRefreshToken;
    
    private String channelName;
    private int songDelaySeconds;

    public TwitchConfigDTO() {}

    public static TwitchConfigDTO fromEntity(TwitchConfig entity) {
        TwitchConfigDTO dto = new TwitchConfigDTO();
        dto.setId(entity.getId());
        dto.setClientId(entity.getClientId());
        dto.setClientSecret(mask(entity.getClientSecret()));
        dto.setAccessToken(mask(entity.getAccessToken()));
        dto.setRefreshToken(mask(entity.getRefreshToken()));
        dto.setBotAccessToken(mask(entity.getBotAccessToken()));
        dto.setBotRefreshToken(mask(entity.getBotRefreshToken()));
        dto.setChannelName(entity.getChannelName());
        dto.setSongDelaySeconds(entity.getSongDelaySeconds());
        return dto;
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        return "********";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getBotAccessToken() { return botAccessToken; }
    public void setBotAccessToken(String botAccessToken) { this.botAccessToken = botAccessToken; }
    public String getBotRefreshToken() { return botRefreshToken; }
    public void setBotRefreshToken(String botRefreshToken) { this.botRefreshToken = botRefreshToken; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public int getSongDelaySeconds() { return songDelaySeconds; }
    public void setSongDelaySeconds(int songDelaySeconds) { this.songDelaySeconds = songDelaySeconds; }
}
