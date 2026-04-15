package dev.phatanon.controller;

import dev.phatanon.dto.SongStatsDTO;
import dev.phatanon.entity.Song;
import dev.phatanon.entity.SongPlay;
import dev.phatanon.repository.SongPlayRepository;
import dev.phatanon.repository.SongRepository;
import dev.phatanon.service.SongService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for managing {@link Song} entities.
 */
@RestController
@RequestMapping("/api/songs")
@Tag(name = "Song Management", description = "Endpoints for managing songs")
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "basicAuth")
public class SongController {

    private static final Logger logger = LoggerFactory.getLogger(SongController.class);

    private final SongRepository songRepository;
    private final SongPlayRepository songPlayRepository;
    private final dev.phatanon.service.TwitchBotService twitchBotService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SongService songService;

    public SongController(SongRepository songRepository, SongPlayRepository songPlayRepository, dev.phatanon.service.TwitchBotService twitchBotService, SimpMessagingTemplate messagingTemplate, SongService songService) {
        this.songRepository = songRepository;
        this.songPlayRepository = songPlayRepository;
        this.twitchBotService = twitchBotService;
        this.messagingTemplate = messagingTemplate;
        this.songService = songService;
    }

    /**
     * Retrieves all available songs from the {@link SongRepository}, ordered by their sort name.
     * @return A list of all {@link Song} entities.
     */
    @GetMapping
    @Operation(summary = "Get all songs")
    public List<Song> getAllSongs() {
        return songRepository.findAllByOrderBySortNameAsc();
    }

    /**
     * Retrieves a specific song by its unique identifier.
     * @param id The unique ID of the song to retrieve.
     * @return A {@link ResponseEntity} containing the {@link Song} if found, or 404 Not Found otherwise.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a song by ID")
    public ResponseEntity<Song> getSongById(@PathVariable Long id) {
        return songRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Adds a new song to the system and broadcasts a refresh message to WebSocket subscribers.
     * Requires an API key for authorization.
     * @param song The {@link Song} entity to be created.
     * @return The newly created {@link Song} entity with its assigned ID.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new song")
    public Song addSong(@RequestBody Song song) {
        songService.updateMetadata(song);
        Song savedSong = songRepository.save(song);
        messagingTemplate.convertAndSend("/topic/songs", "refresh");
        songService.updateM3uFile();
        return savedSong;
    }

    /**
     * Updates an existing song's details and broadcasts a refresh message to WebSocket subscribers.
     * Optionally updates the cover art if a new image file is provided.
     * Requires an API key for authorization.
     * @param id The unique ID of the song to update.
     * @param songDetails The new details to be applied to the song (as JSON string or part).
     * @param coverArt Optional new cover art image file.
     * @return A {@link ResponseEntity} containing the updated {@link Song} if successful, or 404 Not Found if not.
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing song with optional cover art",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(type = "object"),
                    schemaProperties = {
                            @io.swagger.v3.oas.annotations.media.SchemaProperty(name = "song", schema = @Schema(implementation = Song.class)),
                            @io.swagger.v3.oas.annotations.media.SchemaProperty(name = "coverArt", schema = @Schema(type = "string", format = "binary"))
                    }
            )))
    public ResponseEntity<Song> updateSong(
            @PathVariable Long id,
            @RequestPart("song") Song songDetails,
            @RequestPart(value = "coverArt", required = false) MultipartFile coverArt) {
        return songRepository.findById(id)
                .map(song -> {
                    song.setName(songDetails.getName());
                    song.setArtist(songDetails.getArtist());
                    song.setUrl(songDetails.getUrl());
                    song.setRedeems(songDetails.getRedeems());
                    song.setEnabled(songDetails.isEnabled());

                    if (coverArt != null && !coverArt.isEmpty()) {
                        try {
                            songService.setCoverArtFromBytes(song, coverArt.getBytes(), coverArt.getContentType());
                        } catch (IOException e) {
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).<Song>build();
                        }
                    } else if (songDetails.getUrl() != null && !songDetails.getUrl().equals(song.getUrl())) {
                        // If URL changed and no new image provided, try to extract from new URL
                        songService.updateMetadata(song);
                    }

                    Song updatedSong = songRepository.save(song);
                    messagingTemplate.convertAndSend("/topic/songs", "refresh");
                    songService.updateM3uFile();
                    return ResponseEntity.ok(updatedSong);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Queues a song for playback via the {@link dev.phatanon.service.TwitchBotService}.
     * Playback is only possible when the stream is currently online.
     * Requires an API key for authorization.
     * @param id The unique ID of the song to play.
     * @param incrementStats Whether to count this play towards song statistics (default: false).
     * @return A {@link ResponseEntity} indicating whether the song was successfully queued.
     */
    @PostMapping("/{id}/play")
    @PreAuthorize("hasAnyRole('STREAMER', 'ADMIN')")
    @Operation(summary = "Play a song by ID")
    public ResponseEntity<String> playSong(@PathVariable Long id, @RequestParam(required = false, defaultValue = "false") boolean incrementStats) {
        /*
        if (!twitchBotService.isStreamOnline()) {
            return ResponseEntity.badRequest().body("Cannot queue song: Stream is offline.");
        }
        */
        return songRepository.findById(id)
                .map(song -> {
                    twitchBotService.playSongById(id, incrementStats);
                    return ResponseEntity.ok().body("Song queued successfully.");
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Clears the song queue and stops the currently playing song.
     * Requires an API key for authorization.
     * @return A {@link ResponseEntity} indicating that the queue was cleared.
     */
    @PostMapping("/clear")
    @PreAuthorize("hasAnyRole('STREAMER', 'ADMIN')")
    @Operation(summary = "Clear song queue and stop playback")
    public ResponseEntity<String> clearQueue() {
        twitchBotService.clearQueue();
        return ResponseEntity.ok().body("Song queue cleared and playback stopped.");
    }

    /**
     * Retrieves the current number of songs in the playback queue.
     * @return The current queue size.
     */
    @GetMapping("/queue-size")
    @Operation(summary = "Get current song queue size")
    public int getQueueSize() {
        return twitchBotService.getQueueSize();
    }

    @Operation(summary = "Get the current song queue")
    @GetMapping("/queue")
    public List<dev.phatanon.service.TwitchBotService.QueuedSong> getQueue() {
        return twitchBotService.getQueue();
    }

    @Operation(summary = "Remove a song from the queue")
    @DeleteMapping("/queue/{index}")
    @PreAuthorize("hasAnyRole('STREAMER', 'ADMIN')")
    public ResponseEntity<Void> removeFromQueue(@PathVariable("index") int index) {
        twitchBotService.removeFromQueue(index);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the currently playing song.
     * @return The currently playing {@link Song}, or 204 No Content if no song is playing.
     */
    @GetMapping("/current")
    @Operation(summary = "Get currently playing song")
    public ResponseEntity<Song> getCurrentlyPlayingSong() {
        Song song = twitchBotService.getCurrentlyPlayingSong();
        if (song == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(song);
    }

    /**
     * Deletes a song from the {@link SongRepository} (Soft delete).
     * Only sets the enabled flag to false and never deletes the row.
     * @param id The ID of the song to delete.
     * @return 204 No Content if successful, or 404 Not Found if the ID does not exist.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a song")
    public ResponseEntity<Void> deleteSong(@PathVariable Long id) {
        return songRepository.findById(id)
                .map(song -> {
                    song.setEnabled(false);
                    songRepository.save(song);
                    messagingTemplate.convertAndSend("/topic/songs", "refresh");
                    songService.updateM3uFile();
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Permanently deletes a song from the {@link SongRepository} and its associated file.
     * @param id The ID of the song to delete permanently.
     * @return 204 No Content if successful, or 404 Not Found if the ID does not exist.
     */
    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Permanently delete a song and its file")
    public ResponseEntity<Void> deleteSongPermanently(@PathVariable Long id) {
        return songRepository.findById(id)
                .map(song -> {
                    // Delete the file if it's a local upload
                    String url = song.getUrl();
                    if (url != null && url.startsWith("/")) {
                        try {
                            String filename = new java.io.File(url.substring(1)).getName();
                            java.nio.file.Path filePath = java.nio.file.Paths.get(songService.getUploadPath()).resolve(filename).normalize();
                            
                            // Ensure the file is within the upload directory
                            if (!filePath.startsWith(java.nio.file.Paths.get(songService.getUploadPath()).normalize())) {
                                logger.error("Attempted path traversal attack for song {}: {}", id, filePath);
                                return ResponseEntity.status(HttpStatus.FORBIDDEN).<Void>build();
                            }

                            java.nio.file.Files.deleteIfExists(filePath);
                            logger.info("Deleted song file: {}", filePath);
                        } catch (java.io.IOException e) {
                            logger.error("Failed to delete song file for song {}: {}", id, e.getMessage());
                        }
                    }

                    songRepository.delete(song);
                    messagingTemplate.convertAndSend("/topic/songs", "refresh");
                    songService.updateM3uFile();
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves recent song plays from the {@link SongPlayRepository}.
     * @param limit The maximum number of plays to retrieve (default is 5).
     * @return A list of the most recent {@link SongPlay} entities.
     */
    @GetMapping("/plays/recent")
    @Operation(summary = "Get recent song plays")
    public List<SongPlay> getRecentPlays(@RequestParam(defaultValue = "5") int limit) {
        return songPlayRepository.findAllByOrderByTimestampDesc(org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * Retrieves song play statistics aggregated by song or artist over a specified time range.
     * @param range The time range for statistics (e.g., 'daily', 'weekly', 'monthly', 'yearly', or 'alltime').
     * @param groupBy The field to group by (e.g., 'song' or 'artist').
     * @return A list of {@link SongStatsDTO} containing the aggregated statistics.
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get song play statistics")
    public List<SongStatsDTO> getStatistics(@RequestParam(defaultValue = "alltime") String range,
                                            @RequestParam(defaultValue = "song") String groupBy) {
        LocalDateTime since;
        switch (range.toLowerCase()) {
            case "daily":
                since = LocalDateTime.now().minusDays(1);
                break;
            case "weekly":
                since = LocalDateTime.now().minusWeeks(1);
                break;
            case "monthly":
                since = LocalDateTime.now().minusMonths(1);
                break;
            case "yearly":
                since = LocalDateTime.now().minusYears(1);
                break;
            case "alltime":
            default:
                since = LocalDateTime.of(1970, 1, 1, 0, 0);
                break;
        }

        if ("artist".equalsIgnoreCase(groupBy)) {
            return songPlayRepository.getStatsByArtist(since);
        } else {
            return songPlayRepository.getStatsBySong(since);
        }
    }

    /**
     * Lists all files in the song upload directory.
     * @return A list of filenames in the upload directory.
     */
    @GetMapping("/files")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all song files")
    public ResponseEntity<List<String>> listFiles() {
        try {
            java.nio.file.Path root = java.nio.file.Paths.get(songService.getUploadPath());
            if (!java.nio.file.Files.exists(root)) {
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }
            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(root)) {
                List<String> files = stream
                        .filter(file -> !java.nio.file.Files.isDirectory(file))
                        .map(file -> file.getFileName().toString())
                        .filter(name -> !name.equals("playlist.m3u"))
                        .sorted()
                        .toList();
                return ResponseEntity.ok(files);
            }
        } catch (IOException e) {
            logger.error("Failed to list song files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Lists ghost records (DB records with missing files).
     * @return A list of song entities whose files are missing.
     */
    @GetMapping("/ghost-records")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List ghost records (DB records with missing files)")
    public List<Song> getGhostRecords() {
        return songService.getGhostRecords();
    }

    /**
     * Deletes a file from the upload directory.
     * @param filename The name of the file to delete.
     * @return 204 No Content if successful, or 404 Not Found if the file does not exist.
     */
    @DeleteMapping("/files/{filename}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a song file")
    public ResponseEntity<Void> deleteFile(@PathVariable String filename) {
        try {
            java.nio.file.Path uploadDir = java.nio.file.Paths.get(songService.getUploadPath()).normalize();
            // Get only the filename part to prevent traversal
            String safeFilename = new java.io.File(filename).getName();
            java.nio.file.Path filePath = uploadDir.resolve(safeFilename).normalize();

            if (!filePath.startsWith(uploadDir)) {
                logger.error("Attempted path traversal attack: {}", filename);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            if (java.nio.file.Files.deleteIfExists(filePath)) {
                logger.info("Deleted file: {}", filePath);
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
            logger.error("Failed to delete file: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
