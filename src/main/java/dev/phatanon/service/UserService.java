package dev.phatanon.service;

import dev.phatanon.entity.Role;
import dev.phatanon.entity.User;
import dev.phatanon.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing user data and authentication.
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Loads user details by username for Spring Security.
     * @param username The username of the user.
     * @return UserDetails object for the authenticated user.
     * @throws UsernameNotFoundException If the user is not found.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.name()))
                        .collect(Collectors.toList())
        );
    }

    /**
     * Retrieves all users from the database.
     * @return List of all User entities.
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Finds a user by their unique ID.
     * @param id The user ID.
     * @return Optional containing the user if found.
     */
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Creates a new user with an encoded password.
     * @param username The username.
     * @param password The raw password (will be encoded).
     * @param roles Set of roles for the user.
     * @return The newly created User entity.
     */
    @Transactional
    public User createUser(String username, String password, Set<Role> roles) {
        User user = new User(username, passwordEncoder.encode(password), roles);
        user.setApiKey(UUID.randomUUID().toString());
        return userRepository.save(user);
    }

    /**
     * Updates an existing user's details.
     * @param id The user ID to update.
     * @param username The updated username.
     * @param password The updated password (optional, will be encoded if provided).
     * @param roles The updated set of roles.
     * @return The updated User entity.
     */
    @Transactional
    public User updateUser(Long id, String username, String password, Set<Role> roles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setUsername(username);
        if (password != null && !password.isEmpty()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        user.setRoles(roles);
        return userRepository.save(user);
    }

    /**
     * Changes a user's password.
     * @param username The username.
     * @param newPassword The new raw password (will be encoded).
     */
    @Transactional
    public void changePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /**
     * Finds a user by their username.
     * @param username The username.
     * @return Optional containing the user if found.
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Deletes a user by their unique ID.
     * @param id The user ID to delete.
     */
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Finds a user by their API key.
     * @param apiKey The API key.
     * @return Optional containing the user if found.
     */
    public Optional<User> findByApiKey(String apiKey) {
        return userRepository.findByApiKey(apiKey);
    }

    /**
     * Rotates (regenerates) the API key for a user.
     * @param username The username.
     * @return The updated User entity with a new API key.
     */
    @Transactional
    public User rotateApiKey(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setApiKey(UUID.randomUUID().toString());
        return userRepository.save(user);
    }

    /**
     * Checks if a user exists with the given username.
     * @param username The username to check.
     * @return true if the user exists, false otherwise.
     */
    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }
}
