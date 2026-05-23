package dev.phatanon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Entity representing a song in the system.
 */
@Entity
@Table(name = "songs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String sortName;
    private String artist;
    private String url;
    private String coverArt;
    private Integer durationSeconds;

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    private void updateSortName() {
        if (name == null) {
            this.sortName = null;
            return;
        }
        String lowerName = name.toLowerCase();
        if (lowerName.startsWith("the ")) {
            this.sortName = name.substring(4) + ", " + name.substring(0, 3);
        } else if (lowerName.startsWith("a ")) {
            this.sortName = name.substring(2) + ", " + name.substring(0, 1);
        } else if (lowerName.startsWith("an ")) {
            this.sortName = name.substring(3) + ", " + name.substring(0, 2);
        } else {
            this.sortName = name;
        }
    }

    public String getSortName() {
        return sortName;
    }

    public void setSortName(String sortName) {
        this.sortName = sortName;
    }

    @jakarta.persistence.ManyToMany(fetch = jakarta.persistence.FetchType.EAGER)
    @jakarta.persistence.JoinTable(
        name = "song_redeem_link",
        joinColumns = @jakarta.persistence.JoinColumn(name = "song_id"),
        inverseJoinColumns = @jakarta.persistence.JoinColumn(name = "redeem_id")
    )
    private java.util.Set<Redeem> redeems = new java.util.HashSet<>();

    @jakarta.persistence.OneToMany(mappedBy = "song", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    private java.util.List<SongChatMessage> chatMessages = new java.util.ArrayList<>();

    @jakarta.persistence.OneToMany(mappedBy = "song", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    private java.util.List<SongEffect> effects = new java.util.ArrayList<>();

    private boolean enabled = true;

    @CreatedBy
    private String createdBy;

    @CreatedDate
    private LocalDateTime createdTimestamp;

    @LastModifiedBy
    private String lastUpdatedBy;

    @LastModifiedDate
    private LocalDateTime lastUpdatedTimestamp;

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

    public void setLastUpdatedTimestamp(LocalDateTime lastUpdatedTimestamp) {
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }
}
