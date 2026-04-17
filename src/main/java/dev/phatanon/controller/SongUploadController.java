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
     * Only MP3 files are supported.
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
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "artist", required = false) String artist) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            // Some systems might not identify mp3 correctly, so check extension as fallback
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".mp3")) {
                return ResponseEntity.badRequest().body("Only audio files (MP3) are allowed");
            }
        }

        try {
            // Create upload directory if it doesn't exist
            Path root = Paths.get(uploadPath);
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }

            // Generate a unique filename to avoid collisions
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null) {
                // Security: Prevent path traversal by getting only the filename
                originalFilename = new java.io.File(originalFilename).getName();
            }
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            // Limit extension length and check for valid extension if needed
            if (extension.length() > 5) {
                extension = ".mp3";
            }
            
            String filename = UUID.randomUUID().toString() + extension;
            Path destinationFile = root.resolve(filename).normalize();

            // Security: Ensure destination is within the upload directory
            if (!destinationFile.startsWith(root.normalize())) {
                logger.error("Attempted path traversal attack via upload: {}", originalFilename);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid filename");
            }

            // Additional extension check
            if (originalFilename != null && !originalFilename.toLowerCase().endsWith(".mp3")) {
                logger.error("Invalid file extension: {}", originalFilename);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Only MP3 files are supported");
            }

            // Save the file
            Files.copy(file.getInputStream(), destinationFile);

            // Create and save Song entity
            Song song = new Song();
            song.setName(name != null && !name.trim().isEmpty() ? name : "Unknown Title");
            song.setArtist(artist != null && !artist.trim().isEmpty() ? artist : "Unknown Artist");
            song.setUrl("/" + filename);
            song.setEnabled(true);

            // Extract metadata (cover art, artist, name from tags)
            songService.updateMetadata(song);

            Song savedSong;
            try {
                savedSong = songRepository.save(song);
                songService.updateM3uFile();
            } catch (Exception e) {
                // If database save fails, delete the "orphan" file
                try {
                    Files.deleteIfExists(destinationFile);
                    logger.warn("Deleted orphan file {} after database save failure", destinationFile);
                } catch (IOException ie) {
                    logger.error("Failed to delete orphan file {}", destinationFile, ie);
                }
                throw e;
            }

            logger.info("Song uploaded successfully: {} by {} to {}", savedSong.getName(), savedSong.getArtist(), destinationFile);

            return ResponseEntity.status(HttpStatus.CREATED).body(savedSong);

        } catch (IOException e) {
            logger.error("Failed to store uploaded file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to store uploaded file: " + e.getMessage());
        }
    }

    /**
     * Extracts metadata from an MP3 file without saving it.
     * Useful for pre-filling the upload form.
     * @param file The MP3 file to extract metadata from.
     * @return A map containing artist, name, and coverArt (Base64) or an error message.
     */
    @Operation(summary = "Extract metadata from an MP3 file")
    @PostMapping("/metadata")
    @PreAuthorize("hasAnyRole('UPLOAD', 'STREAMER', 'ADMIN')")
    public ResponseEntity<?> extractMetadata(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file");
        }

        File tempFile = null;
        try {
            // Create a temporary file to analyze
            String originalFilename = file.getOriginalFilename();
            String suffix = ".mp3";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            tempFile = File.createTempFile("upload-metadata-", suffix);
            file.transferTo(tempFile);

            Song song = new Song();
            song.setUrl(tempFile.getAbsolutePath());
            
            songService.updateMetadata(song);

            // Create a simple map or DTO to return only what's needed
            java.util.Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("name", song.getName() != null ? song.getName() : "Unknown Title");
            metadata.put("artist", song.getArtist() != null ? song.getArtist() : "Unknown Artist");
            
            logger.info("Extracted metadata for prefill: {} - {}", metadata.get("name"), metadata.get("artist"));

            return ResponseEntity.ok(metadata);
        } catch (IOException e) {
            logger.error("Failed to extract metadata from temporary file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to analyze file: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
