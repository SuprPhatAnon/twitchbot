package dev.phatanon.repository;

import dev.phatanon.dto.SongStatsDTO;
import dev.phatanon.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for {@link Song} entity.
 */
@Repository
public interface SongRepository extends JpaRepository<Song, Long> {
    /**
     * Finds all songs with their total play counts.
     * @return A list of songs with play counts.
     */
    @Query("SELECT new dev.phatanon.dto.SongStatsDTO(s, (SELECT COUNT(sp) FROM SongPlay sp WHERE sp.song = s)) FROM Song s")
    List<SongStatsDTO> findAllWithPlayCount();

    /**
     * Finds all enabled songs with their total play counts.
     * @return A list of enabled songs with play counts.
     */
    @Query("SELECT new dev.phatanon.dto.SongStatsDTO(s, (SELECT COUNT(sp) FROM SongPlay sp WHERE sp.song = s)) FROM Song s WHERE s.enabled = true")
    List<SongStatsDTO> findAllEnabledWithPlayCount();

    /**
     * Finds all songs sorted by sortName.
     * @return A list of all songs sorted by sortName.
     */
    List<Song> findAllByOrderBySortNameAsc();

    /**
     * Finds all songs sorted by created timestamp descending.
     * @return A list of all songs sorted by created timestamp descending.
     */
    List<Song> findAllByOrderByCreatedTimestampDesc();

    /**
     * Finds songs that are linked to a specific redeem title.
     * @param redeemTitle The title of the channel point redeem.
     * @return A list of songs matching the redeem title.
     */
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Song s JOIN s.redeems r WHERE r.title = :redeemTitle")
    List<Song> findByRedeemTitle(String redeemTitle);

    /**
     * Finds all enabled songs.
     * @param enabled The enabled status.
     * @return A list of songs matching the enabled status.
     */
    List<Song> findByEnabled(boolean enabled);

    /**
     * Finds all enabled songs sorted by sortName.
     * @return A list of all enabled songs sorted by sortName.
     */
    List<Song> findAllByEnabledTrueOrderBySortNameAsc();
}
