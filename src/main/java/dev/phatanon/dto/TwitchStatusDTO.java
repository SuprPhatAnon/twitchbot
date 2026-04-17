package dev.phatanon.dto;

import java.util.Map;

/**
 * Data Transfer Object for Twitch status information.
 */
public class TwitchStatusDTO {
    private boolean streamOnline;
    private boolean streamerConnected;
    private boolean botConnected;
    private Map<String, String> subscriptionStatuses;

    public TwitchStatusDTO() {}

    public boolean isStreamOnline() { return streamOnline; }
    public void setStreamOnline(boolean streamOnline) { this.streamOnline = streamOnline; }

    public boolean isStreamerConnected() { return streamerConnected; }
    public void setStreamerConnected(boolean streamerConnected) { this.streamerConnected = streamerConnected; }

    public boolean isBotConnected() { return botConnected; }
    public void setBotConnected(boolean botConnected) { this.botConnected = botConnected; }

    public Map<String, String> getSubscriptionStatuses() { return subscriptionStatuses; }
    public void setSubscriptionStatuses(Map<String, String> subscriptionStatuses) { this.subscriptionStatuses = subscriptionStatuses; }
}
