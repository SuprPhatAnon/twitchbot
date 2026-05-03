package dev.phatanon.service;

import dev.phatanon.entity.Song;
import dev.phatanon.entity.SongPlay;
import dev.phatanon.repository.SongPlayRepository;
import dev.phatanon.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class SongPlayStatsTest {

    @Autowired
    private TwitchBotService twitchBotService;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private SongRepository songRepository;

    @MockitoBean
    private SongPlayRepository songPlayRepository;

    @BeforeEach
    void setUp() {
        twitchBotService.clearQueue();
        reset(songRepository, songPlayRepository);
    }

    @Test
    void shouldNotIncrementStatsWhenQueued() {
        Song song1 = new Song("Song 1", "Artist 1", "url1");
        song1.setId(1L);
        Song song2 = new Song("Song 2", "Artist 2", "url2");
        song2.setId(2L);

        when(songRepository.findById(1L)).thenReturn(Optional.of(song1));
        when(songRepository.findById(2L)).thenReturn(Optional.of(song2));

        // Start playing song 1 (will increment stats as it starts playing immediately)
        twitchBotService.playSongById(1L, true);
        verify(songPlayRepository, times(1)).save(any(SongPlay.class));

        // Queue song 2 (should NOT increment stats yet)
        twitchBotService.playSongById(2L, true);
        verify(songPlayRepository, times(1)).save(any(SongPlay.class)); // Still only the first one
    }

    @Test
    void shouldIncrementStatsWhenStartingFromQueue() throws InterruptedException {
        Song song1 = new Song("Song 1", "Artist 1", "url1");
        song1.setId(1L);
        Song song2 = new Song("Song 2", "Artist 2", "url2");
        song2.setId(2L);

        when(songRepository.findById(1L)).thenReturn(Optional.of(song1));
        when(songRepository.findById(2L)).thenReturn(Optional.of(song2));

        // Start playing song 1
        twitchBotService.playSongById(1L, true);
        
        // Queue song 2
        twitchBotService.playSongById(2L, true);

        // Finish song 1, which should eventually trigger song 2
        twitchBotService.handleSongFinished();
        
        // Wait for scheduler (default 5 seconds, but in test it might be different or we just wait)
        // Since it's a real ScheduledExecutorService in Spring context, we might need to wait.
        // Actually, we can check how TwitchBotService initializes it.
        
        Thread.sleep(6000); // Wait for more than 5 seconds

        verify(songPlayRepository, times(2)).save(any(SongPlay.class));
    }
}
