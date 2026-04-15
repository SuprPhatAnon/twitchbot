package dev.phatanon.service;

import dev.phatanon.entity.Role;
import dev.phatanon.entity.User;
import dev.phatanon.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void loadUserByUsername_UserExists_ReturnsUserDetails() {
        User user = new User("testuser", "password", Set.of(Role.ROLE_ADMIN));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        UserDetails userDetails = userService.loadUserByUsername("testuser");

        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
        assertEquals("password", userDetails.getPassword());
        assertTrue(userDetails.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_UserDoesNotExist_ThrowsException() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.loadUserByUsername("nonexistent"));
    }

    @Test
    void findAll_ReturnsAllUsers() {
        List<User> users = List.of(new User("user1", "p1", Set.of()), new User("user2", "p2", Set.of()));
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.findAll();

        assertEquals(2, result.size());
        assertEquals("user1", result.get(0).getUsername());
    }

    @Test
    void createUser_EncodesPasswordAndSaves() {
        when(passwordEncoder.encode("rawPassword")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = userService.createUser("newuser", "rawPassword", Set.of(Role.ROLE_UPLOAD));

        assertNotNull(result);
        assertEquals("encodedPassword", result.getPassword());
        assertNotNull(result.getApiKey());
        verify(passwordEncoder).encode("rawPassword");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void findByApiKey_ReturnsUser() {
        User user = new User("test", "pass", Set.of());
        user.setApiKey("test-api-key");
        when(userRepository.findByApiKey("test-api-key")).thenReturn(Optional.of(user));
        
        Optional<User> result = userService.findByApiKey("test-api-key");
        
        assertTrue(result.isPresent());
        assertEquals("test", result.get().getUsername());
    }

    @Test
    void rotateApiKey_UpdatesApiKey() {
        User user = new User("testuser", "pass", Set.of());
        String oldKey = "old-key";
        user.setApiKey(oldKey);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = userService.rotateApiKey("testuser");

        assertNotEquals(oldKey, result.getApiKey());
        assertNotNull(result.getApiKey());
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_UpdatesPassword() {
        User user = new User("testuser", "oldPass", Set.of());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNewPass");

        userService.changePassword("testuser", "newPass");

        assertEquals("encodedNewPass", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void deleteUser_CallsRepository() {
        userService.deleteUser(1L);
        verify(userRepository).deleteById(1L);
    }

    @Test
    void existsByUsername_ReturnsTrueIfFound() {
        when(userRepository.findByUsername("test")).thenReturn(Optional.of(new User()));
        assertTrue(userService.existsByUsername("test"));
    }

    @Test
    void findById_ReturnsUser() {
        User user = new User("test", "pass", Set.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        Optional<User> result = userService.findById(1L);
        
        assertTrue(result.isPresent());
        assertEquals("test", result.get().getUsername());
    }

    @Test
    void findByUsername_ReturnsUser() {
        User user = new User("test", "pass", Set.of());
        when(userRepository.findByUsername("test")).thenReturn(Optional.of(user));
        
        Optional<User> result = userService.findByUsername("test");
        
        assertTrue(result.isPresent());
        assertEquals("test", result.get().getUsername());
    }

    @Test
    void updateUser_ExistingUser_UpdatesFields() {
        User user = new User("old", "oldPass", Set.of(Role.ROLE_UPLOAD));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNewPass");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = userService.updateUser(1L, "new", "newPass", Set.of(Role.ROLE_ADMIN));

        assertNotNull(result);
        assertEquals("new", result.getUsername());
        assertEquals("encodedNewPass", result.getPassword());
        assertTrue(result.getRoles().contains(Role.ROLE_ADMIN));
        verify(userRepository).save(user);
    }

    @Test
    void updateUser_ExistingUserNoPasswordChange_KeepsOldPassword() {
        User user = new User("old", "oldPass", Set.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArguments()[0]);

        User result = userService.updateUser(1L, "new", null, Set.of());

        assertEquals("oldPass", result.getPassword());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void updateUser_NonExistentUser_ThrowsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.updateUser(1L, "new", "pass", Set.of()));
    }

    @Test
    void changePassword_NonExistentUser_ThrowsException() {
        when(userRepository.findByUsername("test")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.changePassword("test", "pass"));
    }
}
