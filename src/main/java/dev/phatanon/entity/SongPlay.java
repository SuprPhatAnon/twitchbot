package dev.phatanon.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing a single instance of a song being played.
 */
@Entity
@Table(name = "song_plays")
public class SongPlay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    private LocalDateTime timestamp;

    private String source; // "manual" or the redeem name

    public SongPlay() {
    }

    public SongPlay(Song song, LocalDateTime timestamp, String source) {
        this.song = song;
        this.timestamp = timestamp;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Song getSong() {
        return song;
    }

    public void setSong(Song song) {
        this.song = song;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
