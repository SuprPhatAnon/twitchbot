package dev.phatanon.repository;

import dev.phatanon.dto.SongStatsDTO;
import dev.phatanon.entity.SongPlay;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SongPlayRepository extends JpaRepository<SongPlay, Long> {
    /**
     * Finds the most recent song plays.
     * @param pageable The pagination information.
     * @return A list of the most recent {@link SongPlay} entities.
     */
    List<SongPlay> findAllByOrderByTimestampDesc(Pageable pageable);

    /**
     * Retrieves aggregated play statistics grouped by song.
     * @param since The start date/time for the statistics.
     * @return A list of {@link SongStatsDTO} with song names and their play counts.
     */
    @Query("SELECT new dev.phatanon.dto.SongStatsDTO(s.name, s.artist, s.coverArt, COUNT(sp)) " +
           "FROM SongPlay sp JOIN sp.song s " +
           "WHERE sp.timestamp >= :since " +
           "GROUP BY s.name, s.artist, s.coverArt " +
           "ORDER BY COUNT(sp) DESC")
    List<SongStatsDTO> getStatsBySong(@Param("since") LocalDateTime since);

    /**
     * Retrieves aggregated play statistics grouped by artist.
     * @param since The start date/time for the statistics.
     * @return A list of {@link SongStatsDTO} with artist names and their play counts.
     */
    @Query("SELECT new dev.phatanon.dto.SongStatsDTO(null, s.artist, COUNT(sp)) " +
           "FROM SongPlay sp JOIN sp.song s " +
           "WHERE sp.timestamp >= :since " +
           "GROUP BY s.artist " +
           "ORDER BY COUNT(sp) DESC")
    List<SongStatsDTO> getStatsByArtist(@Param("since") LocalDateTime since);
}
