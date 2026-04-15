package dev.phatanon.service.chat;

import com.github.twitch4j.chat.ITwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import dev.phatanon.model.ChatMessageContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MusicCommandHandlerTest {

    @Test
    void testCanHandle() {
        MusicCommandHandler handler = new MusicCommandHandler("http://localhost");
        
        ChatMessageContext musicContext = ChatMessageContext.builder().message("!music").build();
        ChatMessageContext otherContext = ChatMessageContext.builder().message("!other").build();
        ChatMessageContext musicCapsContext = ChatMessageContext.builder().message("!MUSIC").build();

        assertTrue(handler.canHandle(musicContext));
        assertTrue(handler.canHandle(musicCapsContext));
        assertFalse(handler.canHandle(otherContext));
    }

    /*
    @Test
    void testHandle() {
        String host = "http://test-host";
        MusicCommandHandler handler = new MusicCommandHandler(host);
        
        ChannelMessageEvent event = mock(ChannelMessageEvent.class);
        ITwitchChat chat = mock(ITwitchChat.class);
        
        // Use doReturn for potentially problematic mocks
        doReturn(chat).when(event).getTwitchChat();
        
        ChatMessageContext context = ChatMessageContext.builder()
                .event(event)
                .message("!music")
                .channelName("testchannel")
                .build();

        handler.handle(context);

        verify(chat).sendMessage(eq("testchannel"), contains(host + "/player.html"));
    }
    */
}
