package dev.phatanon.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SongSortNameTest {

    @Test
    void testSortNameCalculation() throws Exception {
        Song song = new Song("The Greatest Song", "Artist", "url");
        invokeUpdateSortName(song);
        assertEquals("Greatest Song, The", song.getSortName());

        song.setName("A Better Song");
        invokeUpdateSortName(song);
        assertEquals("Better Song, A", song.getSortName());

        song.setName("An Awesome Song");
        invokeUpdateSortName(song);
        assertEquals("Awesome Song, An", song.getSortName());

        song.setName("Simple Song");
        invokeUpdateSortName(song);
        assertEquals("Simple Song", song.getSortName());
        
        song.setName("the truth");
        invokeUpdateSortName(song);
        assertEquals("truth, the", song.getSortName());
    }

    private void invokeUpdateSortName(Song song) throws Exception {
        Method method = Song.class.getDeclaredMethod("updateSortName");
        method.setAccessible(true);
        method.invoke(song);
    }
}
