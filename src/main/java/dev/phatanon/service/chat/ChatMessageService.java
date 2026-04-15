package dev.phatanon.service.chat;

import dev.phatanon.model.ChatMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMessageService {
    private static final Logger log = LoggerFactory.getLogger(ChatMessageService.class);
    private final List<ChatMessageHandler> handlers;

    public ChatMessageService(List<ChatMessageHandler> handlers) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(ChatMessageHandler::getOrder))
                .collect(Collectors.toList());
        log.info("Initialized ChatMessageService with {} handlers: {}", 
                handlers.size(), 
                handlers.stream().map(h -> h.getClass().getSimpleName()).collect(Collectors.joining(", ")));
    }

    public void processMessage(ChatMessageContext context) {
        for (ChatMessageHandler handler : handlers) {
            try {
                if (handler.canHandle(context)) {
                    log.debug("Handler {} is processing message: {}", handler.getClass().getSimpleName(), context.getMessage());
                    handler.handle(context);
                }
            } catch (Exception e) {
                log.error("Error in chat message handler {}: {}", handler.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
}
