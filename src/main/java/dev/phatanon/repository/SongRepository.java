package dev.phatanon.repository;

import dev.phatanon.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for {@link Song} entity.
 */
@Repository
public interface SongRepository extends JpaRepository<Song, Long> {
    /**
     * Finds songs by their associated redeem name.
     * @param redeemName The name of the channel point redeem.
     * @return A list of songs matching the redeem name.
     */
    List<Song> findByRedeemName(String redeemName);

    /**
     * Finds all enabled songs.
     * @param enabled The enabled status.
     * @return A list of songs matching the enabled status.
     */
    List<Song> findByEnabled(boolean enabled);
}
