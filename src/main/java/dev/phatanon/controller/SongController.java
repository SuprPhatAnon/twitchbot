package dev.phatanon.controller;

import dev.phatanon.entity.Song;
import dev.phatanon.repository.SongRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public SongController(SongRepository songRepository, dev.phatanon.service.TwitchBotService twitchBotService) {
        this.songRepository = songRepository;
        this.twitchBotService = twitchBotService;
    }

    /**
     * Retrieves all songs from the repository.
     * @return A list of all songs.
     */
    @GetMapping
    @Operation(summary = "Get all songs")
    public List<Song> getAllSongs() {
        return songRepository.findAll();
    }

    /**
     * Retrieves a song by its ID.
     * @param id The ID of the song.
     * @return The song if found, or 404 Not Found.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a song by ID")
    public ResponseEntity<Song> getSongById(@PathVariable Long id) {
        return songRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Adds a new song to the repository.
     * @param song The song to add.
     * @return The saved song.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new song")
    public Song addSong(@RequestBody Song song) {
        return songRepository.save(song);
    }

    /**
     * Updates an existing song.
     * @param id The ID of the song to update.
     * @param songDetails The new details for the song.
     * @return The updated song if found, or 404 Not Found.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update an existing song")
    public ResponseEntity<Song> updateSong(@PathVariable Long id, @RequestBody Song songDetails) {
        return songRepository.findById(id)
                .map(song -> {
                    song.setName(songDetails.getName());
                    song.setArtist(songDetails.getArtist());
                    song.setUrl(songDetails.getUrl());
                    song.setRedeemName(songDetails.getRedeemName());
                    song.setEnabled(songDetails.isEnabled());
                    return ResponseEntity.ok(songRepository.save(song));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Triggers playback of a song by its ID.
     * @param id The ID of the song to play.
     * @return Success message if queued, or error status.
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
     * Deletes a song from the repository.
     * @param id The ID of the song to delete.
     * @return No Content if successful, or 404 Not Found.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a song")
    public ResponseEntity<Void> deleteSong(@PathVariable Long id) {
        return songRepository.findById(id)
                .map(song -> {
                    songRepository.delete(song);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
