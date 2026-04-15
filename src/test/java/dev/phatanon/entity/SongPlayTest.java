package dev.phatanon.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class SongPlayTest {
    @Test
    void testSongPlayProperties() {
        Song song = new Song();
        song.setId(1L);
        LocalDateTime now = LocalDateTime.now();
        SongPlay play = new SongPlay(song, now, "manual");

        assertEquals(song, play.getSong());
        assertEquals(now, play.getTimestamp());
        assertEquals("manual", play.getSource());

        play.setId(10L);
        assertEquals(10L, play.getId());
    }
}
