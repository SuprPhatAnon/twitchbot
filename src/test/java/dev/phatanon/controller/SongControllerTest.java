package dev.phatanon.controller;

import dev.phatanon.dto.SongStatsDTO;
import dev.phatanon.entity.Redeem;
import dev.phatanon.entity.Song;
import dev.phatanon.entity.SongPlay;
import dev.phatanon.repository.SongPlayRepository;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.repository.TwitchConfigRepository;
import dev.phatanon.service.SongService;
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

import org.springframework.mock.web.MockMultipartFile;
import java.time.LocalDateTime;
import java.util.List;
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
    private SongPlayRepository songPlayRepository;

    @Mock
    private TwitchBotService twitchBotService;

    @Mock
    private TwitchConfigRepository twitchConfigRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SongService songService;

    @InjectMocks
    private SongController songController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(songController).build();
    }

    @Test
    void shouldGetAllSongs() throws Exception {
        when(songRepository.findAllByEnabledTrueOrderBySortNameAsc()).thenReturn(java.util.List.of(
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
    void shouldGetAllSongsSortedByNewest() throws Exception {
        when(songRepository.findAllByOrderByCreatedTimestampDesc()).thenReturn(java.util.List.of(
                new Song("Song 2", "Artist 2", "url2"),
                new Song("Song 1", "Artist 1", "url1")
        ));

        mockMvc.perform(get("/api/songs").param("sort", "newest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name", is("Song 2")))
                .andExpect(jsonPath("$[1].name", is("Song 1")));
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
    void shouldAddSongWithoutRedeems() throws Exception {
        Song song = new Song("New Song", "New Artist", "new_url");
        song.setId(1L);
        when(songRepository.save(any(Song.class))).thenReturn(song);

        String songJson = "{\"name\": \"New Song\", \"artist\": \"New Artist\", \"url\": \"new_url\", \"redeems\": []}";

        mockMvc.perform(post("/api/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(songJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("New Song")))
                .andExpect(jsonPath("$.redeems", hasSize(0)));
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
        MockMultipartFile songPart = new MockMultipartFile("song", "", "application/json", updateJson.getBytes());

        mockMvc.perform(multipart("/api/songs/1")
                        .file(songPart)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Song")))
                .andExpect(jsonPath("$.redeems[0].title", is("Updated Redeem")))
                .andExpect(jsonPath("$.enabled", is(false)));
    }

    @Test
    void shouldUpdateSongWithCoverArt() throws Exception {
        Song song = new Song("Old Song", "Old Artist", "old_url");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(songRepository.save(any(Song.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String updateJson = "{\"name\": \"Updated Song\", \"artist\": \"Updated Artist\", \"url\": \"old_url\", \"redeems\": [], \"enabled\": true}";
        MockMultipartFile songPart = new MockMultipartFile("song", "", "application/json", updateJson.getBytes());
        MockMultipartFile coverPart = new MockMultipartFile("coverArt", "test.jpg", "image/jpeg", "image data".getBytes());

        mockMvc.perform(multipart("/api/songs/1")
                        .file(songPart)
                        .file(coverPart)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Song")));

        org.mockito.Mockito.verify(songService).setCoverArtFromBytes(any(Song.class), any(byte[].class), any(String.class));
    }

    @Test
    void shouldDeleteSongSoftly() throws Exception {
        Song song = new Song("Delete Me", "Artist", "url");
        song.setId(1L);
        song.setEnabled(true);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(songRepository.save(any(Song.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(delete("/api/songs/1"))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(songRepository).save(song);
        org.junit.jupiter.api.Assertions.assertFalse(song.isEnabled());
    }

    @Test
    void shouldNotReturnDisabledSongsInGetAllSongs() throws Exception {
        Song song1 = new Song("Song 1", "Artist 1", "url1");
        song1.setEnabled(true);
        Song song2 = new Song("Song 2", "Artist 2", "url2");
        song2.setEnabled(false);

        when(songRepository.findAllByEnabledTrueOrderBySortNameAsc()).thenReturn(List.of(song1));

        mockMvc.perform(get("/api/songs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Song 1")));
    }

    @Test
    void shouldNotGetDisabledSongById() throws Exception {
        Song song = new Song("Disabled Song", "Artist", "url");
        song.setEnabled(false);
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));

        mockMvc.perform(get("/api/songs/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldNotPlayDisabledSong() throws Exception {
        Song song = new Song("Disabled Song", "Artist", "url");
        song.setEnabled(false);
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));

        mockMvc.perform(post("/api/songs/1/play"))
                .andExpect(status().isBadRequest());
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
        
        org.mockito.Mockito.verify(twitchBotService).playSongById(1L, false);
    }

    @Test
    void shouldPlaySongWithStats() throws Exception {
        Song song = new Song("Play Me", "Artist", "url");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(twitchBotService.isStreamOnline()).thenReturn(true);

        mockMvc.perform(post("/api/songs/1/play").param("incrementStats", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string("Song queued successfully."));

        org.mockito.Mockito.verify(twitchBotService).playSongById(1L, true);
    }

    @Test
    void shouldPlaySongWhenStreamOffline() throws Exception {
        Song song = new Song("Play Me", "Artist", "url");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(twitchBotService.isStreamOnline()).thenReturn(false);

        mockMvc.perform(post("/api/songs/1/play"))
                .andExpect(status().isOk())
                .andExpect(content().string("Song queued successfully."));
        
        org.mockito.Mockito.verify(twitchBotService).playSongById(1L, false);
    }

    @Test
    void shouldPlayRandomSong() throws Exception {
        mockMvc.perform(post("/api/songs/random/play"))
                .andExpect(status().isOk())
                .andExpect(content().string("Random song queued successfully."));

        org.mockito.Mockito.verify(twitchBotService).playRandomSong();
    }

    @Test
    void shouldClearQueue() throws Exception {
        mockMvc.perform(post("/api/songs/clear")
                .header("X-API-Key", "test_key"))
                .andExpect(status().isOk())
                .andExpect(content().string("Song queue cleared and playback stopped."));

        org.mockito.Mockito.verify(twitchBotService).clearQueue();
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

    @Test
    void shouldDeleteSongPermanently() throws Exception {
        Song song = new Song("Delete Me Forever", "Artist", "/testfile.mp3");
        song.setId(1L);
        when(songRepository.findById(1L)).thenReturn(Optional.of(song));
        when(songService.getUploadPath()).thenReturn("/tmp");

        mockMvc.perform(delete("/api/songs/1/permanent"))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(songRepository).delete(song);
        org.mockito.Mockito.verify(songService).updateM3uFile();
    }

    @Test
    void shouldListFiles() throws Exception {
        when(songService.getUploadPath()).thenReturn(System.getProperty("java.io.tmpdir"));

        mockMvc.perform(get("/api/songs/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void shouldDeleteFile() throws Exception {
        String filename = "nonexistent_file.mp3";
        when(songService.getUploadPath()).thenReturn(System.getProperty("java.io.tmpdir"));

        mockMvc.perform(delete("/api/songs/files/" + filename))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldGetRecentPlays() throws Exception {
        Song song = new Song("Played Song", "Artist", "url");
        SongPlay play = new SongPlay(song, java.time.LocalDateTime.now(), "manual");
        when(songPlayRepository.findAllByOrderByTimestampDesc(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(java.util.List.of(play));

        mockMvc.perform(get("/api/songs/plays/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].song.name", is("Played Song")))
                .andExpect(jsonPath("$[0].source", is("manual")));
    }

    @Test
    void shouldGetStatisticsBySong() throws Exception {
        SongStatsDTO stat = new SongStatsDTO("Song 1", "Artist 1", 5);
        when(songPlayRepository.getStatsBySong(any(LocalDateTime.class)))
                .thenReturn(List.of(stat));

        mockMvc.perform(get("/api/songs/statistics")
                        .param("range", "daily")
                        .param("groupBy", "song"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Song 1")))
                .andExpect(jsonPath("$[0].playCount", is(5)));
    }

    @Test
    void shouldGetStatisticsByArtist() throws Exception {
        SongStatsDTO stat = new SongStatsDTO(null, "Artist 1", 10);
        when(songPlayRepository.getStatsByArtist(any(LocalDateTime.class)))
                .thenReturn(List.of(stat));

        mockMvc.perform(get("/api/songs/statistics")
                        .param("range", "alltime")
                        .param("groupBy", "artist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].artist", is("Artist 1")))
                .andExpect(jsonPath("$[0].playCount", is(10)));
    }
    @Test
    void shouldListOnlyMp3Files() throws Exception {
        java.nio.file.Path tempUploadDir = java.nio.file.Files.createTempDirectory("song-uploads-test");
        java.nio.file.Files.createFile(tempUploadDir.resolve("song1.mp3"));
        java.nio.file.Files.createFile(tempUploadDir.resolve("song2.MP3"));
        java.nio.file.Files.createFile(tempUploadDir.resolve("not-a-song.txt"));
        java.nio.file.Files.createFile(tempUploadDir.resolve("playlist.m3u"));
        java.nio.file.Files.createDirectory(tempUploadDir.resolve("some-dir"));

        when(songService.getUploadPath()).thenReturn(tempUploadDir.toString());

        mockMvc.perform(get("/api/songs/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0]", is("song1.mp3")))
                .andExpect(jsonPath("$[1]", is("song2.MP3")));

        // Cleanup
        java.nio.file.Files.walk(tempUploadDir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(java.nio.file.Path::toFile)
                .forEach(java.io.File::delete);
    }
}
