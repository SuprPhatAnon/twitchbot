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
    List<SongPlay> findAllByOrderByTimestampDesc(Pageable pageable);

    @Query("SELECT new dev.phatanon.dto.SongStatsDTO(s.name, s.artist, COUNT(sp)) " +
           "FROM SongPlay sp JOIN sp.song s " +
           "WHERE sp.timestamp >= :since " +
           "GROUP BY s.name, s.artist " +
           "ORDER BY COUNT(sp) DESC")
    List<SongStatsDTO> getStatsBySong(@Param("since") LocalDateTime since);

    @Query("SELECT new dev.phatanon.dto.SongStatsDTO(null, s.artist, COUNT(sp)) " +
           "FROM SongPlay sp JOIN sp.song s " +
           "WHERE sp.timestamp >= :since " +
           "GROUP BY s.artist " +
           "ORDER BY COUNT(sp) DESC")
    List<SongStatsDTO> getStatsByArtist(@Param("since") LocalDateTime since);
}
