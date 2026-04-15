package dev.phatanon.service;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;
import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Paths;
import java.util.Base64;

@Service
public class SongService {

    private static final Logger logger = LoggerFactory.getLogger(SongService.class);
    private final SongRepository songRepository;

    public SongService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

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
}
