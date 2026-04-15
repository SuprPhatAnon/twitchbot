package dev.phatanon.model;

public class ChatMessageContext {
    private final String message;
    private final String senderName;
    private final String channelName;

    public ChatMessageContext(String message, String senderName, String channelName) {
        this.message = message;
        this.senderName = senderName;
        this.channelName = channelName;
    }

    public static ChatMessageContextBuilder builder() {
        return new ChatMessageContextBuilder();
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
        private String message;
        private String senderName;
        private String channelName;

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
            return new ChatMessageContext(message, senderName, channelName);
        }
    }
}
