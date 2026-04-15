package dev.phatanon.service;

import dev.phatanon.entity.Song;
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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TwitchBotServiceLogicTest {

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
    void shouldPlaySongImmediatelyWhenNothingIsPlaying() {
        Song song = new Song("Test Song", "Artist", "url");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));

        twitchBotService.playSongById(1L, true);

        assertTrue(twitchBotService.isSongPlaying());
        assertEquals(song, twitchBotService.getCurrentlyPlayingSong());
        verify(messagingTemplate).convertAndSend(eq("/topic/play"), eq(song));
        verify(songRepository).save(song);
        verify(songPlayRepository).save(any());
    }

    @Test
    void shouldQueueSongWhenSomethingIsPlaying() {
        Song song1 = new Song("Song 1", "Artist 1", "url1");
        song1.setId(1L);
        Song song2 = new Song("Song 2", "Artist 2", "url2");
        song2.setId(2L);

        when(songRepository.findById(1L)).thenReturn(Optional.of(song1));
        when(songRepository.findById(2L)).thenReturn(Optional.of(song2));

        // Start playing first song
        twitchBotService.playSongById(1L, true);
        
        // Queue second song
        twitchBotService.playSongById(2L, true);

        assertEquals(1, twitchBotService.getQueueSize());
        assertEquals(song1, twitchBotService.getCurrentlyPlayingSong());
        // Verify only song1 was sent to /topic/play
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/play"), any(Song.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/play"), eq(song1));
    }

    @Test
    void shouldClearQueue() {
        Song song = new Song("Song", "Artist", "url");
        when(songRepository.findById(anyLong())).thenReturn(Optional.of(song));

        twitchBotService.playSongById(1L); // Starts playing
        twitchBotService.playSongById(2L); // Queued
        twitchBotService.playSongById(3L); // Queued

        assertEquals(2, twitchBotService.getQueueSize());

        twitchBotService.clearQueue();

        assertEquals(0, twitchBotService.getQueueSize());
        assertFalse(twitchBotService.isSongPlaying());
        assertNull(twitchBotService.getCurrentlyPlayingSong());
    }

    @Test
    void shouldPlayNextSongAfterFinished() {
        Song song1 = new Song("Song 1", "Artist 1", "url1");
        song1.setId(1L);
        Song song2 = new Song("Song 2", "Artist 2", "url2");
        song2.setId(2L);

        when(songRepository.findById(1L)).thenReturn(Optional.of(song1));
        when(songRepository.findById(2L)).thenReturn(Optional.of(song2));

        twitchBotService.playSongById(1L);
        twitchBotService.playSongById(2L);

        twitchBotService.handleSongFinished();

        assertFalse(twitchBotService.isSongPlaying());
        
        // Capture the task scheduled
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(runnableCaptor.capture(), anyLong(), eq(TimeUnit.SECONDS));

        // Execute the scheduled task
        runnableCaptor.getValue().run();

        assertTrue(twitchBotService.isSongPlaying());
        assertEquals(song2, twitchBotService.getCurrentlyPlayingSong());
        assertEquals(0, twitchBotService.getQueueSize());
    }

    @Test
    void shouldPlayRandomSong() {
        Song song1 = new Song("S1", "A1", "u1");
        song1.setEnabled(true);
        Song song2 = new Song("S2", "A2", "u2");
        song2.setEnabled(true);

        when(songRepository.findByEnabled(true)).thenReturn(List.of(song1, song2));

        twitchBotService.playRandomSong();

        assertTrue(twitchBotService.isSongPlaying());
        assertNotNull(twitchBotService.getCurrentlyPlayingSong());
    }

    @Test
    void shouldHandleEmptySongListForRandomSong() {
        when(songRepository.findByEnabled(true)).thenReturn(List.of());

        twitchBotService.playRandomSong();

        assertFalse(twitchBotService.isSongPlaying());
    }

    @Test
    void shouldGetRecentRedeems() {
        Song song = new Song("S1", "A1", "u1");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        
        twitchBotService.playSongById(1L);
        
        List<TwitchBotService.RedeemLog> redeems = twitchBotService.getRecentRedeems();
        assertNotNull(redeems);
    }

    @Test
    void shouldBroadcastSongsRefresh() {
        twitchBotService.broadcastSongsRefresh();
        verify(messagingTemplate).convertAndSend(eq("/topic/songs"), eq("refresh"));
    }

    @Test
    void shouldBroadcastQueueSize() {
        twitchBotService.broadcastQueueSize();
        verify(messagingTemplate).convertAndSend(eq("/topic/queue-size"), anyInt());
    }

    @Test
    void shouldBroadcastCurrentSong() {
        Song song = new Song("S1", "A1", "u1");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        twitchBotService.playSongById(1L);
        
        twitchBotService.broadcastCurrentSong();
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/current-song"), eq(song));
    }

    @Test
    void shouldNotPlayMissingSongById() {
        when(songRepository.findById(1L)).thenReturn(Optional.empty());
        twitchBotService.playSongById(1L);
        assertFalse(twitchBotService.isSongPlaying());
    }

    @Test
    void shouldHandleSongFinished() {
        Song song = new Song("S1", "A1", "u1");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        twitchBotService.playSongById(1L);
        
        twitchBotService.handleSongFinished();
        
        assertFalse(twitchBotService.isSongPlaying());
        assertNull(twitchBotService.getCurrentlyPlayingSong());
        verify(scheduler).schedule(any(Runnable.class), anyLong(), any());
    }
}
