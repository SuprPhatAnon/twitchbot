package dev.phatanon.model;

public class ChatMessageContext {
    private final String message;
    private final String senderName;
    private final String channelName;
    private final String source;

    public ChatMessageContext(String message, String senderName, String channelName, String source) {
        this.message = message;
        this.senderName = senderName;
        this.channelName = channelName;
        this.source = source;
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

    public String getSource() {
        return source;
    }

    public static class ChatMessageContextBuilder {
        private String message;
        private String senderName;
        private String channelName;
        private String source;

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

        public ChatMessageContextBuilder source(String source) {
            this.source = source;
            return this;
        }

        public ChatMessageContext build() {
            return new ChatMessageContext(message, senderName, channelName, source);
        }
    }
}
