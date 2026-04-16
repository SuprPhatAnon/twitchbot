package dev.phatanon.dto;

import com.github.twitch4j.client.websocket.domain.WebsocketConnectionState;
import java.util.Map;

/**
 * Data Transfer Object for Twitch status information.
 */
public class TwitchStatusDTO {
    private boolean streamOnline;
    private WebsocketConnectionState streamerConnectionState;
    private WebsocketConnectionState botConnectionState;
    private Map<String, Boolean> streamerSubscriptionStatus;
    private Map<String, Boolean> botSubscriptionStatus;

    public TwitchStatusDTO() {}

    public boolean isStreamOnline() { return streamOnline; }
    public void setStreamOnline(boolean streamOnline) { this.streamOnline = streamOnline; }

    public WebsocketConnectionState getStreamerConnectionState() { return streamerConnectionState; }
    public void setStreamerConnectionState(WebsocketConnectionState streamerConnectionState) { this.streamerConnectionState = streamerConnectionState; }

    public WebsocketConnectionState getBotConnectionState() { return botConnectionState; }
    public void setBotConnectionState(WebsocketConnectionState botConnectionState) { this.botConnectionState = botConnectionState; }

    public Map<String, Boolean> getStreamerSubscriptionStatus() { return streamerSubscriptionStatus; }
    public void setStreamerSubscriptionStatus(Map<String, Boolean> streamerSubscriptionStatus) { this.streamerSubscriptionStatus = streamerSubscriptionStatus; }

    public Map<String, Boolean> getBotSubscriptionStatus() { return botSubscriptionStatus; }
    public void setBotSubscriptionStatus(Map<String, Boolean> botSubscriptionStatus) { this.botSubscriptionStatus = botSubscriptionStatus; }
}
