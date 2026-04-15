package dev.phatanon.service.chat;

import dev.phatanon.model.ChatMessageContext;

/**
 * Interface for handling Twitch chat message events.
 */
public interface ChatMessageHandler {
    /**
     * Determines if this handler should process the given message.
     * @param context The chat message context.
     * @return true if it should handle the message, false otherwise.
     */
    boolean canHandle(ChatMessageContext context);

    /**
     * Handles the chat message.
     * @param context The chat message context.
     */
    void handle(ChatMessageContext context);

    /**
     * Returns the order of priority for this handler.
     * Lower values have higher priority.
     * @return The priority value.
     */
    default int getOrder() {
        return 0;
    }
}
