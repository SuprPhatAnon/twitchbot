package dev.phatanon.service.chat;

import dev.phatanon.entity.CustomCommand;
import dev.phatanon.model.ChatMessageContext;
import dev.phatanon.repository.CustomCommandRepository;
import dev.phatanon.service.TwitchBotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class CustomCommandHandler implements ChatMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(CustomCommandHandler.class);

    private final CustomCommandRepository repository;
    private final TwitchBotService twitchBotService;
    private final String redirectUriHost;
    private final ApplicationContext applicationContext;

    public CustomCommandHandler(
            CustomCommandRepository repository,
            @Lazy TwitchBotService twitchBotService,
            @Value("${twitch.redirect-uri-host}") String redirectUriHost,
            ApplicationContext applicationContext) {
        this.repository = repository;
        this.twitchBotService = twitchBotService;
        this.redirectUriHost = redirectUriHost;
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean canHandle(ChatMessageContext context) {
        String message = context.getMessage().trim();
        if (!message.startsWith("!")) {
            return false;
        }
        String commandName = message.split("\\s+")[0];
        return repository.findByCommandNameIgnoreCaseAndEnabledTrue(commandName).isPresent();
    }

    @Override
    public void handle(ChatMessageContext context) {
        String message = context.getMessage().trim();
        String[] parts = message.split("\\s+");
        String commandName = parts[0];
        Optional<CustomCommand> commandOpt = repository.findByCommandNameIgnoreCaseAndEnabledTrue(commandName);
        
        commandOpt.ifPresent(command -> {
            if (command.getJavaMethodName() != null && !command.getJavaMethodName().isBlank()) {
                handleJavaMethod(command.getJavaMethodName(), context, parts);
            } else {
                String response = command.getResponse().replace("${twitch.redirect-uri-host}", redirectUriHost);
                twitchBotService.sendChatMessage(response);
            }
        });
    }

    private void handleJavaMethod(String beanName, ChatMessageContext context, String[] parts) {
        try {
            CustomCommandJavaHandler handler = applicationContext.getBean(beanName, CustomCommandJavaHandler.class);
            String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];
            handler.handle(context.getSenderName(), args);
        } catch (Exception e) {
            logger.error("Error executing java method handler for bean: {}", beanName, e);
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
