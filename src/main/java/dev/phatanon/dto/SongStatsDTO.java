package dev.phatanon.dto;

/**
 * Data Transfer Object for song play statistics.
 * Used for reporting aggregated play counts for songs or artists.
 */
public class SongStatsDTO {
    private String name;
    private String artist;
    private String coverArt;
    private long playCount;

    public SongStatsDTO(String name, String artist, String coverArt, long playCount) {
        this.name = name;
        this.artist = artist;
        this.coverArt = coverArt;
        this.playCount = playCount;
    }

    public SongStatsDTO(String name, String artist, long playCount) {
        this(name, artist, null, playCount);
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

    public String getCoverArt() {
        return coverArt;
    }

    public void setCoverArt(String coverArt) {
        this.coverArt = coverArt;
    }

    public long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(long playCount) {
        this.playCount = playCount;
    }
}
