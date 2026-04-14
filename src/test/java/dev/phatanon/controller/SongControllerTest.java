package dev.phatanon.controller;

import dev.phatanon.entity.Redeem;
import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SongControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SongRepository songRepository;

    @Mock
    private TwitchBotService twitchBotService;

    @Mock
    private TwitchConfigRepository twitchConfigRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private SongController songController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(songController).build();
    }

    @Test
    void shouldGetAllSongs() throws Exception {
        when(songRepository.findAllByOrderBySortNameAsc()).thenReturn(java.util.List.of(
                new Song("Song 1", "Artist 1", "url1"),
                new Song("Song 2", "Artist 2", "url2")
        ));

        mockMvc.perform(get("/api/songs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Song 1")))
                .andExpect(jsonPath("$[1].name", is("Song 2")));
    }

    @Test
    void shouldGetSongById() throws Exception {
        Song song = new Song("Song 1", "Artist 1", "url1");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));

        mockMvc.perform(get("/api/songs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Song 1")));
    }

    @Test
    void shouldAddSong() throws Exception {
        Redeem redeem = new Redeem("Play Random Song");
        redeem.setId(1L);
        Song song = new Song("New Song", "New Artist", "new_url", redeem);
        song.setId(1L);
        when(songRepository.save(any(Song.class))).thenReturn(song);

        String songJson = "{\"name\": \"New Song\", \"artist\": \"New Artist\", \"url\": \"new_url\", \"redeems\": [{\"id\": 1, \"title\": \"Play Random Song\"}]}";

        mockMvc.perform(post("/api/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(songJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("New Song")))
                .andExpect(jsonPath("$.redeems[0].title", is("Play Random Song")));
    }

    @Test
    void shouldUpdateSong() throws Exception {
        Redeem oldRedeem = new Redeem("Old Redeem");
        oldRedeem.setId(1L);
        Song song = new Song("Old Song", "Old Artist", "old_url", oldRedeem);
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(songRepository.save(any(Song.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String updateJson = "{\"name\": \"Updated Song\", \"artist\": \"Updated Artist\", \"url\": \"updated_url\", \"redeems\": [{\"id\": 2, \"title\": \"Updated Redeem\"}], \"enabled\": false}";

        mockMvc.perform(put("/api/songs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Song")))
                .andExpect(jsonPath("$.redeems[0].title", is("Updated Redeem")))
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void shouldDeleteSong() throws Exception {
        Song song = new Song("Delete Me", "Artist", "url");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/songs/1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/songs/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldPlaySong() throws Exception {
        Song song = new Song("Play Me", "Artist", "url");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(twitchBotService.isStreamOnline()).thenReturn(true);

        mockMvc.perform(post("/api/songs/1/play"))
                .andExpect(status().isOk())
                .andExpect(content().string("Song queued successfully."));
    }

    @Test
    void shouldNotPlaySongWhenStreamOffline() throws Exception {
        when(twitchBotService.isStreamOnline()).thenReturn(false);

        mockMvc.perform(post("/api/songs/1/play"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot queue song: Stream is offline."));
    }

    @Test
    void shouldGetQueueSize() throws Exception {
        when(twitchBotService.getQueueSize()).thenReturn(5);

        mockMvc.perform(get("/api/songs/queue-size"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void shouldGetCurrentlyPlayingSong() throws Exception {
        Song song = new Song("Now Playing", "Artist", "url");
        when(twitchBotService.getCurrentlyPlayingSong()).thenReturn(song);

        mockMvc.perform(get("/api/songs/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Now Playing")));
    }

    @Test
    void shouldReturnNoContentWhenNoSongPlaying() throws Exception {
        when(twitchBotService.getCurrentlyPlayingSong()).thenReturn(null);

        mockMvc.perform(get("/api/songs/current"))
                .andExpect(status().isNoContent());
    }
}
