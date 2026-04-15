package dev.phatanon.service.chat;

import dev.phatanon.model.ChatMessageContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class ChatMessageServiceTest {

    @Test
    void testProcessMessage_CallsHandlers() {
        ChatMessageHandler handler1 = mock(ChatMessageHandler.class);
        ChatMessageHandler handler2 = mock(ChatMessageHandler.class);
        
        when(handler1.getOrder()).thenReturn(1);
        when(handler2.getOrder()).thenReturn(2);
        
        ChatMessageService service = new ChatMessageService(List.of(handler1, handler2));
        ChatMessageContext context = ChatMessageContext.builder().message("test").build();
        
        when(handler1.canHandle(context)).thenReturn(true);
        when(handler2.canHandle(context)).thenReturn(false);
        
        service.processMessage(context);
        
        verify(handler1).handle(context);
        verify(handler2, never()).handle(context);
    }

    @Test
    void testProcessMessage_HandlesException() {
        ChatMessageHandler handler = mock(ChatMessageHandler.class);
        ChatMessageService service = new ChatMessageService(List.of(handler));
        ChatMessageContext context = ChatMessageContext.builder().message("test").build();
        
        when(handler.canHandle(context)).thenReturn(true);
        doThrow(new RuntimeException("test error")).when(handler).handle(context);
        
        // Should not throw exception
        service.processMessage(context);
        
        verify(handler).handle(context);
    }
}
