package dev.phatanon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity representing a song in the system.
 */
@Entity
@Table(name = "songs")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String artist;
    private String url;

    @jakarta.persistence.ManyToMany(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinTable(
        name = "song_redeem_link",
        joinColumns = @jakarta.persistence.JoinColumn(name = "song_id"),
        inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "redeem_id")
    )
    private java.util.Set<Redeem> redeems = new java.util.HashSet<>();

    private boolean enabled = true;

    public Song() {
    }

    public Song(String name, String artist, String url) {
        this.name = name;
        this.artist = artist;
        this.url = url;
    }

    public Song(String name, String artist, String url, Redeem redeem) {
        this.name = name;
        this.artist = artist;
        this.url = url;
        if (redeem != null) {
            this.redeems.add(redeem);
        }
    }

    public Song(String name, String artist, String url, Redeem redeem, boolean enabled) {
        this.name = name;
        this.artist = artist;
        this.url = url;
        if (redeem != null) {
            this.redeems.add(redeem);
        }
        this.enabled = enabled;
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

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public java.util.Set<Redeem> getRedeems() {
        return redeems;
    }

    public void setRedeems(java.util.Set<Redeem> redeems) {
        this.redeems = redeems;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
