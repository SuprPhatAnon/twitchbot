package dev.phatanon.controller;

import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.service.SongService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Controller for handling song file uploads.
 */
@RestController
@RequestMapping("/api/songs/upload")
@Tag(name = "Song Upload", description = "Endpoints for uploading song files")
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "basicAuth")
public class SongUploadController {

    private static final Logger logger = LoggerFactory.getLogger(SongUploadController.class);

    private final SongRepository songRepository;
    private final SongService songService;

    @Value("${twitch.song-upload-path}")
    private String uploadPath;

    public SongUploadController(SongRepository songRepository, SongService songService) {
        this.songRepository = songRepository;
        this.songService = songService;
    }

    /**
     * Uploads a new song file and creates a corresponding Song entity.
     * @param file The multipart file to upload.
     * @param name The name of the song.
     * @param artist The artist of the song.
     * @return The created Song entity or an error message.
     */
    @Operation(summary = "Upload a new song file")
    @PostMapping
    @PreAuthorize("hasAnyRole('UPLOAD', 'STREAMER', 'ADMIN')")
    public ResponseEntity<?> uploadSong(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("artist") String artist) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload");
        }

        try {
            // Create upload directory if it doesn't exist
            Path root = Paths.get(uploadPath);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }

            // Generate a unique filename to avoid collisions
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString() + extension;
            Path destinationFile = root.resolve(filename);

            // Save the file
            Files.copy(file.getInputStream(), destinationFile);

            // Create and save Song entity
            Song song = new Song();
            song.setName(name);
            song.setArtist(artist);
            song.setUrl("/" + filename);
            song.setEnabled(true);

            // Extract cover art if possible
            songService.updateCoverArt(song);

            Song savedSong = songRepository.save(song);
            songService.updateM3uFile();

            logger.info("Song uploaded successfully: {} by {} to {}", name, artist, destinationFile);

            return ResponseEntity.status(HttpStatus.CREATED).body(savedSong);

        } catch (IOException e) {
            logger.error("Failed to store uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to store uploaded file: " + e.getMessage());
        }
    }
}
