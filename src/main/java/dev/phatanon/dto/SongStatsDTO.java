package dev.phatanon.dto;

/**
 * Data Transfer Object for song play statistics.
 * Used for reporting aggregated play counts for songs or artists.
 */
public class SongStatsDTO {
    private String name;
    private String artist;
    private long playCount;

    public SongStatsDTO(String name, String artist, long playCount) {
        this.name = name;
        this.artist = artist;
        this.playCount = playCount;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(long playCount) {
        this.playCount = playCount;
    }
}
