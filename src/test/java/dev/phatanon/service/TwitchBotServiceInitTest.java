package dev.phatanon.service;

import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.helix.TwitchHelix;
import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;
import dev.phatanon.entity.TwitchConfig;
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
    private TwitchClient twitchClient;
    @Mock
    private EventManager eventManager;
    @Mock
    private TwitchHelix helix;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        twitchBotService = new TwitchBotService(
                messagingTemplate,
                songRepository,
                songPlayRepository,
                twitchConfigRepository,
                scheduler
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
    }
}
