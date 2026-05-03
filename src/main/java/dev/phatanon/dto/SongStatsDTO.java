package dev.phatanon.dto;

/**
 * Data Transfer Object for song play statistics.
 * Used for reporting aggregated play counts for songs or artists.
 */
public class SongStatsDTO {
    private Long id;
    private String name;
    private String artist;
    private String coverArt;
    private String url;
    private Integer durationSeconds;
    private long playCount;
    private String sortName;
    private java.time.LocalDateTime createdTimestamp;
    private java.util.List<dev.phatanon.entity.Redeem> redeems;

    public SongStatsDTO(Long id, String name, String artist, String coverArt, String url, Integer durationSeconds, long playCount, String sortName, java.time.LocalDateTime createdTimestamp) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.coverArt = coverArt;
        this.url = url;
        this.durationSeconds = durationSeconds;
        this.playCount = playCount;
        this.sortName = sortName;
        this.createdTimestamp = createdTimestamp;
        this.redeems = new java.util.ArrayList<>();
    }

    public SongStatsDTO(dev.phatanon.entity.Song song, long playCount) {
        this.id = song.getId();
        this.name = song.getName();
        this.artist = song.getArtist();
        this.coverArt = song.getCoverArt();
        this.url = song.getUrl();
        this.durationSeconds = song.getDurationSeconds();
        this.playCount = playCount;
        this.sortName = song.getSortName();
        this.createdTimestamp = song.getCreatedTimestamp();
        this.redeems = new java.util.ArrayList<>(song.getRedeems());
    }

    public SongStatsDTO(String name, String artist, String coverArt, long playCount) {
        this.name = name;
        this.artist = artist;
        this.coverArt = coverArt;
        this.playCount = playCount;
        this.redeems = new java.util.ArrayList<>();
    }

    public SongStatsDTO(String artist, long playCount) {
        this.artist = artist;
        this.playCount = playCount;
        this.redeems = new java.util.ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(long playCount) {
        this.playCount = playCount;
    }

    public String getSortName() {
        return sortName;
    }

    public void setSortName(String sortName) {
        this.sortName = sortName;
    }

    public java.time.LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(java.time.LocalDateTime createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public java.util.List<dev.phatanon.entity.Redeem> getRedeems() {
        return redeems;
    }

    public void setRedeems(java.util.List<dev.phatanon.entity.Redeem> redeems) {
        this.redeems = redeems;
    }
}
