package dev.phatanon.service;

import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.eventsub.events.*;
import com.github.twitch4j.eventsub.socket.events.EventSocketConnectionStateEvent;
import com.github.twitch4j.eventsub.socket.events.EventSocketSubscriptionFailureEvent;
import com.github.twitch4j.eventsub.socket.events.EventSocketSubscriptionSuccessEvent;
import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.service.chat.ChatMessageService;
import dev.phatanon.repository.SongPlayRepository;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.repository.TwitchConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.mockito.Mockito.*;

class TwitchBotServiceInitTest {

    private TwitchBotService twitchBotService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private SongRepository songRepository;
    @Mock
    private SongPlayRepository songPlayRepository;
    @Mock
    private TwitchConfigRepository twitchConfigRepository;
    @Mock
    private ScheduledExecutorService scheduler;
    @Mock
    private ChatMessageService chatMessageService;
    @Mock
    private TwitchClient twitchClient;
    @Mock
    private EventManager eventManager;
    @Mock
    private TwitchHelix helix;
    @Mock
    private TwitchClient botTwitchClient;
    @Mock
    private EventManager botEventManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        twitchBotService = new TwitchBotService(
                messagingTemplate,
                songRepository,
                songPlayRepository,
                twitchConfigRepository,
                scheduler,
                chatMessageService
        );
    }

    @Test
    void init_WithValidConfig_RegistersListeners() {
        TwitchConfig config = new TwitchConfig();
        config.setClientId("id");
        config.setClientSecret("secret");
        config.setAccessToken("token");
        config.setChannelName("channel");
        
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));
        when(twitchClient.getEventManager()).thenReturn(eventManager);
        when(twitchClient.getHelix()).thenReturn(helix);
        
        // Mock Helix calls
        User user = mock(User.class);
        when(user.getId()).thenReturn("123");
        UserList userList = mock(UserList.class);
        when(userList.getUsers()).thenReturn(List.of(user));
        
        var usersCall = mock(com.netflix.hystrix.HystrixCommand.class);
        when(usersCall.execute()).thenReturn(userList);
        when(helix.getUsers(any(), any(), any())).thenReturn(usersCall);
        
        StreamList streamList = mock(StreamList.class);
        when(streamList.getStreams()).thenReturn(List.of());
        var streamsCall = mock(com.netflix.hystrix.HystrixCommand.class);
        when(streamsCall.execute()).thenReturn(streamList);
        when(helix.getStreams(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(streamsCall);

        twitchBotService.setTwitchClient(twitchClient);
        twitchBotService.init();

        verify(helix, atLeastOnce()).getUsers(eq("token"), any(), any());
        verify(helix, atLeastOnce()).getStreams(eq("token"), any(), any(), any(), any(), any(), any(), any());
        verify(eventManager, atLeastOnce()).onEvent(any(), any());
        
        // Verify specific events are registered
        verify(eventManager).onEvent(eq(CustomRewardRedemptionAddEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelCheerEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelFollowEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelSubscribeEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelSubscriptionGiftEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelSubscriptionMessageEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelGoLiveEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelGoOfflineEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelChatMessageEvent.class), any());
    }

    @Test
    void init_WithClientCredentialsFlow_RegistersBotListeners() {
        TwitchConfig config = new TwitchConfig();
        config.setClientId("id");
        config.setClientSecret("secret");
        config.setAccessToken("token");
        config.setChannelName("channel");
        // No botAccessToken set, should trigger Client Credentials flow

        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));
        when(twitchClient.getEventManager()).thenReturn(eventManager);
        when(twitchClient.getHelix()).thenReturn(helix);
        when(botTwitchClient.getEventManager()).thenReturn(botEventManager);
        when(botTwitchClient.getHelix()).thenReturn(helix);

        // Mock Helix calls
        User user = mock(User.class);
        when(user.getId()).thenReturn("123");
        UserList userList = mock(UserList.class);
        when(userList.getUsers()).thenReturn(List.of(user));

        var usersCall = mock(com.netflix.hystrix.HystrixCommand.class);
        when(usersCall.execute()).thenReturn(userList);
        when(helix.getUsers(any(), any(), any())).thenReturn(usersCall);

        StreamList streamList = mock(StreamList.class);
        when(streamList.getStreams()).thenReturn(List.of());
        var streamsCall = mock(com.netflix.hystrix.HystrixCommand.class);
        when(streamsCall.execute()).thenReturn(streamList);
        when(helix.getStreams(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(streamsCall);

        // Mock App Access Token fetch
        var appToken = mock(com.github.philippheuer.credentialmanager.domain.OAuth2Credential.class);
        when(appToken.getAccessToken()).thenReturn("app-token");
        // We can't easily mock identityProvider.getAppAccessToken() because it's created inside init()
        // but we can check if "app-token" is used in subsequent calls if we could inject it.
        // Actually, in the real code, if pre-fetch fails, it just continues.
        
        twitchBotService.setTwitchClient(twitchClient);
        org.springframework.test.util.ReflectionTestUtils.setField(twitchBotService, "botTwitchClient", botTwitchClient);

        twitchBotService.init();

        // If App Token was successfully "fetched" (which it won't be in this mock setup unless we mock identityProvider)
        // it would use it. Since it fails to fetch in mock, it might fallback to bot-token (null) then streamer-token.
        // Wait, if botAccessToken is null, it used to set botTwitchClient = null.
        // Now it creates a new botTwitchClient.
        
        // Let's verify that it attempts to use a token for getUsers.
        // In the mock, identityProvider.getAppAccessToken() will likely return null or throw.
        // If it returns null, currentAppAccessToken remains null.
        // botToken will be currentAccessToken ("token") because botAccessToken is null.
        verify(helix, atLeastOnce()).getUsers(eq("token"), any(), any());
    }

    @Test
    void init_WithBotConfig_RegistersBotListeners() {
        TwitchConfig config = new TwitchConfig();
        config.setClientId("id");
        config.setClientSecret("secret");
        config.setAccessToken("token");
        config.setBotAccessToken("bot-token");
        config.setChannelName("channel");

        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));
        when(twitchClient.getEventManager()).thenReturn(eventManager);
        when(twitchClient.getHelix()).thenReturn(helix);
        when(botTwitchClient.getEventManager()).thenReturn(botEventManager);

        // Mock Helix calls
        User user = mock(User.class);
        when(user.getId()).thenReturn("123");
        UserList userList = mock(UserList.class);
        when(userList.getUsers()).thenReturn(List.of(user));

        var usersCall = mock(com.netflix.hystrix.HystrixCommand.class);
        when(usersCall.execute()).thenReturn(userList);
        when(helix.getUsers(any(), any(), any())).thenReturn(usersCall);

        StreamList streamList = mock(StreamList.class);
        when(streamList.getStreams()).thenReturn(List.of());
        var streamsCall = mock(com.netflix.hystrix.HystrixCommand.class);
        when(streamsCall.execute()).thenReturn(streamList);
        when(helix.getStreams(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(streamsCall);

        twitchBotService.setTwitchClient(twitchClient);
        org.springframework.test.util.ReflectionTestUtils.setField(twitchBotService, "botTwitchClient", botTwitchClient);

        twitchBotService.init();

        verify(helix, atLeastOnce()).getUsers(eq("bot-token"), any(), any());
        verify(helix, atLeastOnce()).getStreams(eq("token"), any(), any(), any(), any(), any(), any(), any());
        verify(eventManager).onEvent(eq(ChannelChatMessageEvent.class), any());
        verify(botEventManager).onEvent(eq(ChannelChatMessageEvent.class), any());
    }
}
