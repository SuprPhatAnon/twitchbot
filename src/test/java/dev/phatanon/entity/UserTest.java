package dev.phatanon.entity;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    @Test
    void testUserProperties() {
        User user = new User();
        user.setId(1L);
        user.setUsername("test");
        user.setPassword("pass");
        user.setRoles(Set.of(Role.ROLE_ADMIN));

        assertEquals(1L, user.getId());
        assertEquals("test", user.getUsername());
        assertEquals("pass", user.getPassword());
        assertEquals(Set.of(Role.ROLE_ADMIN), user.getRoles());
    }

    @Test
    void testUserConstructor() {
        User user = new User("test", "pass", Set.of(Role.ROLE_UPLOAD));
        assertEquals("test", user.getUsername());
        assertEquals("pass", user.getPassword());
        assertEquals(Set.of(Role.ROLE_UPLOAD), user.getRoles());
    }
}
