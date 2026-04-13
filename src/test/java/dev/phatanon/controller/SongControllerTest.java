package dev.phatanon.controller;

import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.service.TwitchBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SongControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SongRepository songRepository;

    @MockBean
    private TwitchBotService twitchBotService;

    @BeforeEach
    void setUp() {
        songRepository.deleteAll();
    }

    @Test
    void shouldGetAllSongs() throws Exception {
        songRepository.save(new Song("Song 1", "Artist 1", "url1"));
        songRepository.save(new Song("Song 2", "Artist 2", "url2"));

        mockMvc.perform(get("/api/songs")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Song 1")))
                .andExpect(jsonPath("$[1].name", is("Song 2")));
    }

    @Test
    void shouldFailWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/songs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldGetSongById() throws Exception {
        Song song = songRepository.save(new Song("Song 1", "Artist 1", "url1"));

        mockMvc.perform(get("/api/songs/" + song.getId())
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Song 1")));
    }

    @Test
    void shouldAddSong() throws Exception {
        String songJson = "{\"name\": \"New Song\", \"artist\": \"New Artist\", \"url\": \"new_url\", \"redeemName\": \"Play Random Song\"}";

        mockMvc.perform(post("/api/songs")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(songJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("New Song")))
                .andExpect(jsonPath("$.redeemName", is("Play Random Song")));
    }

    @Test
    void shouldUpdateSong() throws Exception {
        Song song = songRepository.save(new Song("Old Song", "Old Artist", "old_url", "Old Redeem"));
        String updateJson = "{\"name\": \"Updated Song\", \"artist\": \"Updated Artist\", \"url\": \"updated_url\", \"redeemName\": \"Updated Redeem\", \"enabled\": false}";

        mockMvc.perform(put("/api/songs/" + song.getId())
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Song")))
                .andExpect(jsonPath("$.redeemName", is("Updated Redeem")))
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void shouldDeleteSong() throws Exception {
        Song song = songRepository.save(new Song("Delete Me", "Artist", "url"));

        mockMvc.perform(delete("/api/songs/" + song.getId())
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/songs/" + song.getId())
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldPlaySong() throws Exception {
        Song song = songRepository.save(new Song("Play Me", "Artist", "url"));
        when(twitchBotService.isStreamOnline()).thenReturn(true);

        mockMvc.perform(post("/api/songs/" + song.getId() + "/play")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(content().string("Song queued successfully."));
    }

    @Test
    void shouldNotPlaySongWhenStreamOffline() throws Exception {
        Song song = songRepository.save(new Song("Play Me", "Artist", "url"));
        when(twitchBotService.isStreamOnline()).thenReturn(false);

        mockMvc.perform(post("/api/songs/" + song.getId() + "/play")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot queue song: Stream is offline."));
    }

    @Test
    void shouldGetQueueSize() throws Exception {
        when(twitchBotService.getQueueSize()).thenReturn(5);

        mockMvc.perform(get("/api/songs/queue-size")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void shouldGetCurrentlyPlayingSong() throws Exception {
        Song song = new Song("Now Playing", "Artist", "url");
        when(twitchBotService.getCurrentlyPlayingSong()).thenReturn(song);

        mockMvc.perform(get("/api/songs/current")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Now Playing")));
    }

    @Test
    void shouldReturnNoContentWhenNoSongPlaying() throws Exception {
        when(twitchBotService.getCurrentlyPlayingSong()).thenReturn(null);

        mockMvc.perform(get("/api/songs/current")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isNoContent());
    }
}
