package dev.phatanon.service;

import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongPlayRepository;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.repository.TwitchConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class TwitchBotServiceWebSocketTest {

    @Autowired
    private TwitchBotService twitchBotService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private SongRepository songRepository;

    @MockitoBean
    private SongPlayRepository songPlayRepository;

    @MockitoBean
    private TwitchConfigRepository twitchConfigRepository;

    @Test
    void shouldBroadcastCurrentSong() {
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        twitchBotService.clearQueue();
        
        // Verification of broadcast on /topic/current-song with "null" (as it clears it)
        verify(messagingTemplate).convertAndSend(eq("/topic/current-song"), eq("null"));
    }
    
    @Test
    void shouldBroadcastSongsRefreshWhenPlayingByIdWithStats() {
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        Song song = new Song("Test Song", "Test Artist", "test_url");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        
        twitchBotService.playSongById(1L, true);
        
        // Verification of broadcast on /topic/songs to refresh UI
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/songs"), eq("refresh"));
        // Should also broadcast current song when it starts playing
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/current-song"), (Object) argThat(argument -> 
            argument instanceof Song s && s.getName().equals(song.getName()) && s.getArtist().equals(song.getArtist())
        ));
    }

    @Test
    void shouldBroadcastQueueSizeWhenAddingToQueue() {
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        Song song1 = new Song("Song 1", "Artist 1", "url1");
        song1.setId(1L);
        Song song2 = new Song("Song 2", "Artist 2", "url2");
        song2.setId(2L);
        
        when(songRepository.findById(1L)).thenReturn(Optional.of(song1));
        when(songRepository.findById(2L)).thenReturn(Optional.of(song2));
        
        // Play first song to make isSongPlaying = true
        twitchBotService.playSongById(1L, false);
        
        // Add second song to queue
        twitchBotService.playSongById(2L, false);
        
        // Verification of broadcast on /topic/queue-size
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/queue-size"), eq(1));
    }

    @Test
    void shouldHandleSongsWithSingleQuote() {
        doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));
        twitchBotService.clearQueue();
        reset(messagingTemplate);
        
        Song song = new Song("Don't Stop Believin'", "Journey", "journey.mp3");
        song.setId(3L);
        song.setEnabled(true);
        when(songRepository.findById(3L)).thenReturn(Optional.of(song));

        twitchBotService.playSongById(3L, false);

        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/play"), argThat((Song s) -> s.getName().equals(song.getName())));
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/current-song"), (Object) argThat(argument -> 
            argument instanceof Song s && s.getName().equals(song.getName()) && s.getArtist().equals(song.getArtist())
        ));
    }
}
