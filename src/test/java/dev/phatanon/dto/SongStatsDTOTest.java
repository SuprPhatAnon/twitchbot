package dev.phatanon.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongStatsDTOTest {

    @Test
    void testGettersAndSetters() {
        SongStatsDTO dto = new SongStatsDTO("Name", "Artist", "Cover", 10L);
        assertEquals("Name", dto.getName());
        assertEquals("Artist", dto.getArtist());
        assertEquals("Cover", dto.getCoverArt());
        assertEquals(10L, dto.getPlayCount());

        dto.setName("NewName");
        dto.setArtist("NewArtist");
        dto.setCoverArt("NewCover");
        dto.setPlayCount(20L);

        assertEquals("NewName", dto.getName());
        assertEquals("NewArtist", dto.getArtist());
        assertEquals("NewCover", dto.getCoverArt());
        assertEquals(20L, dto.getPlayCount());
    }

    @Test
    void testConstructorWithoutCoverArt() {
        SongStatsDTO dto = new SongStatsDTO("Name", "Artist", null, 10L);
        assertEquals("Name", dto.getName());
        assertEquals("Artist", dto.getArtist());
        assertNull(dto.getCoverArt());
        assertEquals(10L, dto.getPlayCount());
    }
}
