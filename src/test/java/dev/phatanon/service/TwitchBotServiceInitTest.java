package dev.phatanon.service;

import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.Stream;
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

import static org.mockito.ArgumentMatchers.*;
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

        verify(eventManager, atLeastOnce()).onEvent(any(), any());
        
        // Verify specific events are registered
        verify(eventManager).onEvent(eq(EventSocketConnectionStateEvent.class), any());
        verify(eventManager).onEvent(eq(CustomRewardRedemptionAddEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelCheerEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelFollowEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelSubscribeEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelSubscriptionGiftEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelSubscriptionMessageEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelGoLiveEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelGoOfflineEvent.class), any());
        verify(eventManager).onEvent(eq(ChannelChatMessageEvent.class), any());
        verify(eventManager).onEvent(eq(EventSocketSubscriptionSuccessEvent.class), any());
        verify(eventManager).onEvent(eq(EventSocketSubscriptionFailureEvent.class), any());
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

        // Use reflection or a way to inject botTwitchClient
        // Since botTwitchClient is built inside init(), we might need to mock TwitchClientBuilder
        // or just verify what happens when botTwitchClient is NOT null.
        // Actually, TwitchBotService builds it. To test this properly without PowerMock,
        // we might need to change how TwitchBotService creates clients.
        
        // For now, let's just verify the main client listeners again in this scenario
        twitchBotService.setTwitchClient(twitchClient);
        // We can't easily set botTwitchClient because it's private and recreated in init()
        // unless we use ReflectionTestUtils
        org.springframework.test.util.ReflectionTestUtils.setField(twitchBotService, "botTwitchClient", botTwitchClient);

        twitchBotService.init();

        verify(eventManager).onEvent(eq(ChannelChatMessageEvent.class), any());
        verify(botEventManager).onEvent(eq(ChannelChatMessageEvent.class), any());
        verify(botEventManager).onEvent(eq(EventSocketConnectionStateEvent.class), any());
        verify(botEventManager).onEvent(eq(EventSocketSubscriptionSuccessEvent.class), any());
        verify(botEventManager).onEvent(eq(EventSocketSubscriptionFailureEvent.class), any());
    }
}
