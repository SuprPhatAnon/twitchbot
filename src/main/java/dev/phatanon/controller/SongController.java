package dev.phatanon.controller;

import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing {@link Song} entities.
 */
@RestController
@RequestMapping("/api/songs")
@Tag(name = "Song Management", description = "Endpoints for managing songs")
@SecurityRequirement(name = "X-API-Key")
public class SongController {

    private final SongRepository songRepository;
    private final dev.phatanon.service.TwitchBotService twitchBotService;
    private final SimpMessagingTemplate messagingTemplate;

    public SongController(SongRepository songRepository, dev.phatanon.service.TwitchBotService twitchBotService, SimpMessagingTemplate messagingTemplate) {
        this.songRepository = songRepository;
        this.twitchBotService = twitchBotService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Retrieves all songs from the {@link SongRepository}.
     * @return A list of all {@link Song} entities.
     */
    @GetMapping
    @Operation(summary = "Get all songs")
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    /**
     * Retrieves a specific song by its unique ID.
     * @param id The ID of the song to retrieve.
     * @return The {@link Song} if found, or 404 Not Found if no song exists with the given ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a song by ID")
    public ResponseEntity<Song> getSongById(@PathVariable Long id) {
        return songRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Adds a new song to the {@link SongRepository}.
     * @param song The {@link Song} entity to create.
     * @return The saved {@link Song} entity with its generated ID.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new song")
    public Song addSong(@RequestBody Song song) {
        Song savedSong = songRepository.save(song);
        messagingTemplate.convertAndSend("/topic/songs", "refresh");
        return savedSong;
    }

    /**
     * Updates an existing song in the {@link SongRepository}.
     * @param id The ID of the song to update.
     * @param songDetails The new details to apply to the existing song.
     * @return The updated {@link Song} if found, or 404 Not Found if the ID does not exist.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update an existing song")
    public ResponseEntity<Song> updateSong(@PathVariable Long id, @RequestBody Song songDetails) {
        return songRepository.findById(id)
                .map(song -> {
                    song.setName(songDetails.getName());
                    song.setArtist(songDetails.getArtist());
                    song.setUrl(songDetails.getUrl());
                    song.setRedeems(songDetails.getRedeems());
                    song.setEnabled(songDetails.isEnabled());
                    Song updatedSong = songRepository.save(song);
                    messagingTemplate.convertAndSend("/topic/songs", "refresh");
                    return ResponseEntity.ok(updatedSong);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Triggers playback of a song by its ID through the {@link dev.phatanon.service.TwitchBotService}.
     * The song will only be queued if the stream is currently online.
     * @param id The ID of the song to play.
     * @return A success message if the song was queued, or an error status (e.g., 400 Bad Request if stream is offline).
     */
    @PostMapping("/{id}/play")
    @Operation(summary = "Play a song by ID")
    public ResponseEntity<String> playSong(@PathVariable Long id) {
        if (!twitchBotService.isStreamOnline()) {
            return ResponseEntity.badRequest().body("Cannot queue song: Stream is offline.");
        }
        return songRepository.findById(id)
                .map(song -> {
                    twitchBotService.playSongById(id);
                    return ResponseEntity.ok().body("Song queued successfully.");
                })
                .orElse(ResponseEntity.notFound().build());
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
     * Deletes a song from the {@link SongRepository}.
     * @param id The ID of the song to delete.
     * @return 204 No Content if successful, or 404 Not Found if the ID does not exist.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a song")
    public ResponseEntity<Void> deleteSong(@PathVariable Long id) {
        return songRepository.findById(id)
                .map(song -> {
                    songRepository.delete(song);
                    messagingTemplate.convertAndSend("/topic/songs", "refresh");
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
