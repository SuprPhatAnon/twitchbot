package dev.phatanon.service;

import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.common.enums.SubscriptionPlan;
import com.github.twitch4j.eventsub.events.*;
import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;
import dev.phatanon.entity.Song;
import dev.phatanon.entity.TwitchConfig;
import dev.phatanon.model.ChatMessageContext;
import dev.phatanon.repository.SongPlayRepository;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.chat.ChatMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwitchBotEventSubTest {

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

    private final Map<Class<?>, Consumer<Object>> eventHandlers = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
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

        TwitchConfig config = new TwitchConfig();
        config.setClientId("id");
        config.setClientSecret("secret");
        config.setAccessToken("token");
        config.setChannelName("testchannel");
        when(twitchConfigRepository.findAll()).thenReturn(List.of(config));

        when(twitchClient.getEventManager()).thenReturn(eventManager);
        when(twitchClient.getHelix()).thenReturn(helix);

        // Mock Helix for init
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

        // Capture event handlers
        doAnswer(invocation -> {
            Class<?> eventClass = invocation.getArgument(0);
            Consumer<Object> handler = invocation.getArgument(1);
            eventHandlers.put(eventClass, handler);
            return null;
        }).when(eventManager).onEvent(any(Class.class), any(Consumer.class));

        twitchBotService.setTwitchClient(twitchClient);
        twitchBotService.init();
    }

    private <T> void triggerEvent(T event) {
        Consumer<Object> handler = eventHandlers.get(event.getClass());
        if (handler != null) {
            handler.accept(event);
        } else {
            fail("No handler registered for event: " + event.getClass().getSimpleName() + ". Registered: " + eventHandlers.keySet());
        }
    }

    @Test
    void testCustomRewardRedemptionAddEvent_TriggersSong() throws Exception {
        CustomRewardRedemptionAddEvent event = mock(CustomRewardRedemptionAddEvent.class);
        
        // Find the Reward class reflectively to avoid compilation issues in agent env
        Class<?> rewardClass = Class.forName("com.github.twitch4j.eventsub.domain.Reward");
        Object rewardMock = mock(rewardClass);
        
        // Use reflection to mock the getTitle method
        java.lang.reflect.Method getTitleMethod = rewardClass.getMethod("getTitle");
        when(getTitleMethod.invoke(rewardMock)).thenReturn("Play Song");
        
        when(event.getReward()).thenAnswer(inv -> rewardMock);
        when(event.getUserName()).thenReturn("testuser");

        Song song = new Song("Test Song", "Artist", "url");
        song.setEnabled(true);
        when(songRepository.findByRedeemTitle("Play Song")).thenReturn(List.of(song));

        triggerEvent(event);

        assertTrue(twitchBotService.isSongPlaying());
        assertEquals(song, twitchBotService.getCurrentlyPlayingSong());
        verify(messagingTemplate).convertAndSend(eq("/topic/redeems"), any(Object.class));
    }

    @Test
    void testChannelChatMessageEvent_CallsChatMessageService() throws Exception {
        ChannelChatMessageEvent event = mock(ChannelChatMessageEvent.class);
        
        // Let's use reflection on the event itself to find the return type of getMessage()
        java.lang.reflect.Method getMessageMethod = ChannelChatMessageEvent.class.getMethod("getMessage");
        Class<?> chatMessageClass = getMessageMethod.getReturnType();
        
        Object messageMock = mock(chatMessageClass);
        
        java.lang.reflect.Method getTextMethod = chatMessageClass.getMethod("getText");
        when(getTextMethod.invoke(messageMock)).thenReturn("!music");
        
        when(event.getMessage()).thenAnswer(inv -> messageMock);
        when(event.getChatterUserName()).thenReturn("testuser");
        when(event.getBroadcasterUserName()).thenReturn("testchannel");

        triggerEvent(event);

        ArgumentCaptor<ChatMessageContext> captor = ArgumentCaptor.forClass(ChatMessageContext.class);
        verify(chatMessageService).processMessage(captor.capture());
        
        ChatMessageContext captured = captor.getValue();
        assertEquals("!music", captured.getMessage());
        assertEquals("testuser", captured.getSenderName());
        assertEquals("testchannel", captured.getChannelName());
    }

    @Test
    void testChannelCheerEvent_LogsRedeem() {
        ChannelCheerEvent event = mock(ChannelCheerEvent.class);
        when(event.getUserName()).thenReturn("cheeruser");
        when(event.getBits()).thenReturn(100);

        triggerEvent(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/redeems"), (Object) any());
    }

    @Test
    void testChannelFollowEvent_LogsRedeem() {
        ChannelFollowEvent event = mock(ChannelFollowEvent.class);
        when(event.getUserName()).thenReturn("follower");

        triggerEvent(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/redeems"), (Object) any());
    }

    @Test
    void testChannelSubscribeEvent_LogsRedeem() {
        ChannelSubscribeEvent event = mock(ChannelSubscribeEvent.class);
        when(event.getUserName()).thenReturn("subscriber");
        when(event.getTier()).thenReturn(SubscriptionPlan.TIER1);

        triggerEvent(event);

        verify(messagingTemplate).convertAndSend(eq("/topic/redeems"), (Object) any());
    }

    @Test
    void testStreamOnlineAndOffline_UpdatesStatus() {
        // Go Live
        StreamOnlineEvent liveEvent = mock(StreamOnlineEvent.class);
        when(liveEvent.getBroadcasterUserName()).thenReturn("testchannel");

        triggerEvent(liveEvent);

        assertTrue(twitchBotService.isStreamOnline());

        // Go Offline
        StreamOfflineEvent offlineEvent = mock(StreamOfflineEvent.class);
        when(offlineEvent.getBroadcasterUserName()).thenReturn("testchannel");

        triggerEvent(offlineEvent);

        assertFalse(twitchBotService.isStreamOnline());
    }
}
