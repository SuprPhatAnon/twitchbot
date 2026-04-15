package dev.phatanon.model;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;

public class ChatMessageContext {
    private final ChannelMessageEvent event;
    private final String message;
    private final String senderName;
    private final String channelName;

    public ChatMessageContext(ChannelMessageEvent event, String message, String senderName, String channelName) {
        this.event = event;
        this.message = message;
        this.senderName = senderName;
        this.channelName = channelName;
    }

    public static ChatMessageContextBuilder builder() {
        return new ChatMessageContextBuilder();
    }

    public ChannelMessageEvent getEvent() {
        return event;
    }

    public String getMessage() {
        return message;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getChannelName() {
        return channelName;
    }

    public static class ChatMessageContextBuilder {
        private ChannelMessageEvent event;
        private String message;
        private String senderName;
        private String channelName;

        public ChatMessageContextBuilder event(ChannelMessageEvent event) {
            this.event = event;
            return this;
        }

        public ChatMessageContextBuilder message(String message) {
            this.message = message;
            return this;
        }

        public ChatMessageContextBuilder senderName(String senderName) {
            this.senderName = senderName;
            return this;
        }

        public ChatMessageContextBuilder channelName(String channelName) {
            this.channelName = channelName;
            return this;
        }

        public ChatMessageContext build() {
            return new ChatMessageContext(event, message, senderName, channelName);
        }
    }
}
