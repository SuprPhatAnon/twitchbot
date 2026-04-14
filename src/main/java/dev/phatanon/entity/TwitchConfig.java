package dev.phatanon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing the Twitch bot configuration and credentials.
 */
@Entity
@Table(name = "twitch_config")
public class TwitchConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String clientId;
    private String clientSecret;
    private String accessToken;
    private String refreshToken;
    private String botAccessToken;
    private String botRefreshToken;
    private String channelName;
    private String redeemTitle;
    private int songDelaySeconds;

    public TwitchConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getBotAccessToken() {
        return botAccessToken;
    }

    public void setBotAccessToken(String botAccessToken) {
        this.botAccessToken = botAccessToken;
    }

    public String getBotRefreshToken() {
        return botRefreshToken;
    }

    public void setBotRefreshToken(String botRefreshToken) {
        this.botRefreshToken = botRefreshToken;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getRedeemTitle() {
        return redeemTitle;
    }

    public void setRedeemTitle(String redeemTitle) {
        this.redeemTitle = redeemTitle;
    }

    public int getSongDelaySeconds() {
        return songDelaySeconds;
    }

    public void setSongDelaySeconds(int songDelaySeconds) {
        this.songDelaySeconds = songDelaySeconds;
    }
}
