package dev.phatanon.controller;

import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.service.SongService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SongUploadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SongRepository songRepository;

    @Mock
    private SongService songService;

    @TempDir
    Path tempDir;

    private SongUploadController songUploadController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        songUploadController = new SongUploadController(songRepository, songService);
        ReflectionTestUtils.setField(songUploadController, "uploadPath", tempDir.toString());
        mockMvc = MockMvcBuilders.standaloneSetup(songUploadController).build();
    }

    @Test
    void uploadSong_ValidFile_ReturnsCreated() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", MediaType.TEXT_PLAIN_VALUE, "fake mp3 content".getBytes());
        
        Song song = new Song();
        song.setName("Test Song");
        song.setArtist("Test Artist");
        when(songRepository.save(any(Song.class))).thenReturn(song);

        mockMvc.perform(multipart("/api/songs/upload")
                .file(file)
                .param("name", "Test Song")
                .param("artist", "Test Artist"))
                .andExpect(status().isCreated());

        verify(songRepository).save(any(Song.class));
        verify(songService).updateM3uFile();
    }

    @Test
    void uploadSong_EmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/songs/upload")
                .file(file)
                .param("name", "Test")
                .param("artist", "Test"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadSong_LargeFile_ReturnsCreated() throws Exception {
        // Create a 10MB dummy file
        byte[] largeContent = new byte[10 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "large.mp3", "audio/mpeg", largeContent);

        Song song = new Song();
        song.setName("Large Song");
        song.setArtist("Large Artist");
        when(songRepository.save(any(Song.class))).thenReturn(song);

        mockMvc.perform(multipart("/api/songs/upload")
                .file(file)
                .param("name", "Large Song")
                .param("artist", "Large Artist"))
                .andExpect(status().isCreated());

        verify(songRepository).save(any(Song.class));
        verify(songService).updateM3uFile();
    }

    @Test
    void extractMetadata_ValidFile_ReturnsMetadata() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.mp3", "audio/mpeg", "fake mp3 content".getBytes());

        mockMvc.perform(multipart("/api/songs/upload/metadata")
                .file(file))
                .andDo(result -> System.out.println("[DEBUG_LOG] Response: " + result.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.artist").exists());

        verify(songService).updateMetadata(any(Song.class));
    }
    @Test
    void uploadSong_InvalidExtension_ReturnsBadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.wav", "audio/wav", "fake wav content".getBytes());

        mockMvc.perform(multipart("/api/songs/upload")
                .file(file)
                .param("name", "Wav Song")
                .param("artist", "Wav Artist"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Only MP3 files are supported"));
    }
}
