package dev.phatanon.service.chat;

import dev.phatanon.model.ChatMessageContext;
import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MusicCommandHandlerTest {

    @Test
    void testCanHandle() {
        MusicCommandHandler handler = new MusicCommandHandler("http://localhost", mock(TwitchBotService.class));
        
        ChatMessageContext musicContext = ChatMessageContext.builder().message("!music").build();
        ChatMessageContext otherContext = ChatMessageContext.builder().message("!other").build();
        ChatMessageContext musicCapsContext = ChatMessageContext.builder().message("!MUSIC").build();

        assertTrue(handler.canHandle(musicContext));
        assertTrue(handler.canHandle(musicCapsContext));
        assertFalse(handler.canHandle(otherContext));
    }

    @Test
    void testHandle() {
        String host = "http://test-host";
        TwitchBotService twitchBotService = mock(TwitchBotService.class);
        MusicCommandHandler handler = new MusicCommandHandler(host, twitchBotService);
        
        ChatMessageContext context = ChatMessageContext.builder()
                .message("!music")
                .channelName("testchannel")
                .build();

        handler.handle(context);

        verify(twitchBotService).sendChatMessage(contains(host + "/player.html"));
    }
}
