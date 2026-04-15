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
     * Extracts cover art from the MP3 file at the given song's URL.
     * @param song The song to update with cover art.
     */
    public void updateCoverArt(Song song) {
        String url = song.getUrl();
        if (url == null || url.isEmpty()) {
            return;
        }

        try {
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

            if (file.exists() && file.isFile()) {
                Mp3File mp3file = new Mp3File(file);
                if (mp3file.hasId3v2Tag()) {
                    ID3v2 id3v2Tag = mp3file.getId3v2Tag();
                    byte[] albumImage = id3v2Tag.getAlbumImage();
                    if (albumImage != null) {
                        String mimeType = id3v2Tag.getAlbumImageMimeType();
                        String base64Image = Base64.getEncoder().encodeToString(albumImage);
                        song.setCoverArt("data:" + mimeType + ";base64," + base64Image);
                        logger.info("Extracted cover art for song: {} ({} bytes)", song.getName(), albumImage.length);
                    } else {
                        song.setCoverArt(null);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract cover art from {}: {}", url, e.getMessage());
            song.setCoverArt(null);
        }
    }

    /**
     * Updates the playlist.m3u file in the song-uploads-pvc directory.
     * The file contains a list of all enabled songs in M3U format.
     */
    public void updateM3uFile() {
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
