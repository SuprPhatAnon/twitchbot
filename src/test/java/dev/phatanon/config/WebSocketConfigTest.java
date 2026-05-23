package dev.phatanon.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.SimpleBrokerRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    @Test
    void configureMessageBroker_ConfiguresBroker() {
        WebSocketConfig config = new WebSocketConfig();
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);
        SimpleBrokerRegistration simpleBrokerRegistration = mock(SimpleBrokerRegistration.class);
        
        when(registry.enableSimpleBroker("/topic")).thenReturn(simpleBrokerRegistration);
        
        config.configureMessageBroker(registry);
        
        verify(registry).enableSimpleBroker("/topic");
        verify(simpleBrokerRegistration).setHeartbeatValue(any(long[].class));
        verify(simpleBrokerRegistration).setTaskScheduler(any());
        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void registerStompEndpoints_RegistersEndpoint() {
        WebSocketConfig config = new WebSocketConfig();
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        
        when(registry.addEndpoint("/ws")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns("*")).thenReturn(registration);
        
        config.registerStompEndpoints(registry);
        
        verify(registry).addEndpoint("/ws");
        verify(registration).setAllowedOriginPatterns("*");
        verify(registration).withSockJS();
    }
}
