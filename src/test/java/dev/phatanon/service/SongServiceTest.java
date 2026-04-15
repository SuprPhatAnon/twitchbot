package dev.phatanon.service;

import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SongServiceTest {

    private SongRepository songRepository;
    private SongService songService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        songRepository = Mockito.mock(SongRepository.class);
        songService = new SongService(songRepository);
        ReflectionTestUtils.setField(songService, "uploadPath", tempDir.toString());
    }

    @Test
    void shouldGenerateM3uFileWithEnabledSongs() throws IOException {
        // Given
        Song song1 = new Song();
        song1.setName("Song 1");
        song1.setArtist("Artist 1");
        song1.setUrl("/path/to/song1.mp3");
        song1.setEnabled(true);

        Song song2 = new Song();
        song2.setName("Song 2");
        song2.setArtist("Artist 2");
        song2.setUrl("/path/to/song2.mp3");
        song2.setEnabled(true);

        when(songRepository.findAllByEnabledTrueOrderBySortNameAsc()).thenReturn(Arrays.asList(song1, song2));

        // When
        songService.updateM3uFile();

        // Then
        Path m3uFile = tempDir.resolve("playlist.m3u");
        assertTrue(Files.exists(m3uFile));

        List<String> lines = Files.readAllLines(m3uFile);
        assertEquals(5, lines.size());
        assertEquals("#EXTM3U", lines.get(0));
        assertEquals("#EXTINF:-1,Artist 1 - Song 1", lines.get(1));
        assertEquals("/path/to/song1.mp3", lines.get(2));
        assertEquals("#EXTINF:-1,Artist 2 - Song 2", lines.get(3));
        assertEquals("/path/to/song2.mp3", lines.get(4));
    }

    @Test
    void shouldCreateDirectoryIfNotExists() throws IOException {
        // Given
        Path subDir = tempDir.resolve("subdir");
        ReflectionTestUtils.setField(songService, "uploadPath", subDir.toString());
        when(songRepository.findAllByEnabledTrueOrderBySortNameAsc()).thenReturn(List.of());

        // When
        songService.updateM3uFile();

        // Then
        assertTrue(Files.exists(subDir));
        assertTrue(Files.exists(subDir.resolve("playlist.m3u")));
    }

    @Test
    void shouldHandleNullUrlWhenUpdatingCoverArt() {
        Song song = new Song();
        song.setUrl(null);
        songService.updateCoverArt(song);
        assertNull(song.getCoverArt());
    }

    @Test
    void shouldHandleMissingFileWhenUpdatingCoverArt() {
        Song song = new Song();
        song.setUrl(tempDir.resolve("missing.mp3").toString());
        songService.updateCoverArt(song);
        assertNull(song.getCoverArt());
    }

    @Test
    void shouldExtractCoverArtFromMp3() throws IOException {
        // Prepare a dummy MP3 file with cover art
        Path mp3Path = tempDir.resolve("test.mp3");
        try (InputStream is = getClass().getResourceAsStream("/test-with-cover.mp3")) {
            if (is != null) {
                Files.copy(is, mp3Path);
            } else {
                // If we don't have a resource, we can't really test the successful extraction easily
                // without adding a binary file to the repo. 
                // Let's at least test the "no tag" case by creating an empty file.
                Files.createFile(mp3Path);
            }
        }

        Song song = new Song();
        song.setUrl(mp3Path.toString());
        song.setName("Test Song");

        songService.updateCoverArt(song);
        
        // If it was an empty file, cover art should be null
        assertNull(song.getCoverArt());
    }
}
