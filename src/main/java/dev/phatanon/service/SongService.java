package dev.phatanon.service;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;

/**
 * Service for managing song files and metadata.
 * Handles metadata extraction from MP3 files and M3U playlist generation.
 */
@Service
public class SongService {

    private static final Logger logger = LoggerFactory.getLogger(SongService.class);
    private final SongRepository songRepository;

    @Value("${twitch.song-upload-path}")
    private String uploadPath;

    public SongService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    /**
     * Extracts cover art, artist, and name from the MP3 file at the given song's URL.
     * @param song The song to update with metadata.
     */
    public void updateMetadata(Song song) {
        String url = song.getUrl();
        if (url == null || url.isEmpty()) {
            return;
        }

        try {
            File file = getFileFromUrl(url);

            if (file != null && file.exists() && file.isFile()) {
                Mp3File mp3file = new Mp3File(file);
                song.setDurationSeconds((int) mp3file.getLengthInSeconds());

                if (mp3file.hasId3v2Tag()) {
                    ID3v2 id3v2Tag = mp3file.getId3v2Tag();

                    // Extract cover art
                    byte[] albumImage = id3v2Tag.getAlbumImage();
                    if (albumImage != null) {
                        String mimeType = id3v2Tag.getAlbumImageMimeType();
                        setCoverArtFromBytes(song, albumImage, mimeType);
                        logger.info("Extracted cover art for song: {} ({} bytes)", song.getName(), albumImage.length);
                    }

                    // Extract Artist if not set
                    if (song.getArtist() == null || song.getArtist().trim().isEmpty() || "Unknown Artist".equalsIgnoreCase(song.getArtist())) {
                        String artist = id3v2Tag.getArtist();
                        if (artist != null && !artist.trim().isEmpty()) {
                            song.setArtist(artist.trim());
                        }
                    }

                    // Extract Title if not set
                    if (song.getName() == null || song.getName().trim().isEmpty() || "Unknown Title".equalsIgnoreCase(song.getName())) {
                        String title = id3v2Tag.getTitle();
                        if (title != null && !title.trim().isEmpty()) {
                            song.setName(title.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract metadata from {}: {}", url, e.getMessage());
        }
    }

    private File getFileFromUrl(String url) {
        // Check if it's a local file
        File file = new File(url);
        if (!file.exists()) {
            // Try relative to current directory if it doesn't exist
            file = Paths.get(url).toFile();
        }

        // If still not exists and starts with /, try relative to uploadPath
        if (!file.exists() && url.startsWith("/")) {
            file = Paths.get(uploadPath, url.substring(1)).toFile();
        }
        return file.exists() ? file : null;
    }

    /**
     * Sets the cover art for a song from a byte array.
     * @param song The song to update.
     * @param imageBytes The image data.
     * @param mimeType The MIME type of the image.
     */
    public void setCoverArtFromBytes(Song song, byte[] imageBytes, String mimeType) {
        if (imageBytes == null) {
            song.setCoverArt(null);
            return;
        }
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        song.setCoverArt("data:" + mimeType + ";base64," + base64Image);
    }

    /**
     * @return The configured upload path for songs.
     */
    public String getUploadPath() {
        return uploadPath;
    }

    /**
     * @return A list of songs that point to non-existent files.
     */
    public List<Song> getGhostRecords() {
        return songRepository.findAll().stream()
                .filter(song -> {
                    String url = song.getUrl();
                    if (url == null || url.isEmpty()) return true;
                    File file = getFileFromUrl(url);
                    return file == null || !file.exists();
                })
                .toList();
    }

    /**
     * Updates the playlist.m3u file in the song-uploads-pvc directory.
     * The file contains a list of all enabled songs in M3U format.
     */
    public synchronized void updateM3uFile() {
        List<Song> enabledSongs = songRepository.findAllByEnabledTrueOrderBySortNameAsc();
        Path m3uPath = Paths.get(uploadPath, "playlist.m3u");

        try {
            // Ensure directory exists
            Path parentDir = m3uPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            try (FileWriter writer = new FileWriter(m3uPath.toFile())) {
                writer.write("#EXTM3U\n");
                for (Song song : enabledSongs) {
                    writer.write("#EXTINF:-1," + song.getArtist() + " - " + song.getName() + "\n");
                    String songUrl = song.getUrl();
                    if (songUrl != null && songUrl.startsWith("/")) {
                        // For M3U in the same directory, use relative path without leading slash
                        writer.write(songUrl.substring(1) + "\n");
                    } else {
                        writer.write(songUrl + "\n");
                    }
                }
            }
            logger.info("Updated playlist.m3u with {} songs at {}", enabledSongs.size(), m3uPath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Failed to update playlist.m3u at {}: {}", m3uPath, e.getMessage());
        }
    }
}
