package dev.phatanon.dto;

import java.util.Map;

/**
 * Data Transfer Object for Twitch status information.
 */
public class TwitchStatusDTO {
    private boolean streamOnline;
    private boolean streamerConnected;
    private boolean botConnected;
    private String channelName;
    private String broadcasterId;
    private String botUserId;
    private int queueSize;
    private Map<String, String> subscriptionStatuses;

    public TwitchStatusDTO() {}

    public boolean isStreamOnline() { return streamOnline; }
    public void setStreamOnline(boolean streamOnline) { this.streamOnline = streamOnline; }

    public boolean isStreamerConnected() { return streamerConnected; }
    public void setStreamerConnected(boolean streamerConnected) { this.streamerConnected = streamerConnected; }

    public boolean isBotConnected() { return botConnected; }
    public void setBotConnected(boolean botConnected) { this.botConnected = botConnected; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public String getBroadcasterId() { return broadcasterId; }
    public void setBroadcasterId(String broadcasterId) { this.broadcasterId = broadcasterId; }

    public String getBotUserId() { return botUserId; }
    public void setBotUserId(String botUserId) { this.botUserId = botUserId; }

    public int getQueueSize() { return queueSize; }
    public void setQueueSize(int queueSize) { this.queueSize = queueSize; }

    public Map<String, String> getSubscriptionStatuses() { return subscriptionStatuses; }
    public void setSubscriptionStatuses(Map<String, String> subscriptionStatuses) { this.subscriptionStatuses = subscriptionStatuses; }
}
