package dev.phatanon.controller;

import dev.phatanon.dto.UserDTO;
import dev.phatanon.entity.Role;
import dev.phatanon.entity.User;
import dev.phatanon.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Endpoints for managing users and their profiles")
@SecurityRequirement(name = "apiKey")
@SecurityRequirement(name = "basicAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Retrieves the profile of the currently authenticated user.
     * @param userDetails The details of the authenticated user.
     * @return The UserDTO for the current user.
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserDTO> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        return userService.findByUsername(userDetails.getUsername())
                .map(user -> ResponseEntity.ok(UserDTO.fromEntity(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Changes the password of the currently authenticated user.
     * @param userDetails The details of the authenticated user.
     * @param request The request containing the new password.
     * @return 200 OK.
     */
    @PostMapping("/me/change-password")
    @Operation(summary = "Change current user's password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserDetails userDetails, @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userDetails.getUsername(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    /**
     * Rotates (regenerates) the API key of the currently authenticated user.
     * @param userDetails The details of the authenticated user.
     * @return The UserDTO with the new raw API key.
     */
    @PostMapping("/me/rotate-api-key")
    @Operation(summary = "Rotate current user's API key")
    public ResponseEntity<UserDTO> rotateApiKey(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.rotateApiKey(userDetails.getUsername());
        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    /**
     * Retrieves a list of all users. Requires ADMIN role.
     * @return List of all UserDTOs.
     */
    @GetMapping
    @Operation(summary = "Get all users (Admin only)")
    public List<UserDTO> getAllUsers() {
        return userService.findAll().stream()
                .map(UserDTO::fromEntity)
                .map(dto -> new UserDTO(dto.id(), dto.username(), dto.roles(), "********")) // Mask API keys for list
                .toList();
    }

    /**
     * Creates a new user. Requires ADMIN role.
     * @param request The user creation request.
     * @return The newly created UserDTO with raw API key or an error message if the username exists.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user (Admin only)")
    public ResponseEntity<?> createUser(@RequestBody UserRequest request) {
        if (userService.existsByUsername(request.username())) {
            return ResponseEntity.badRequest().body("Username already exists");
        }
        User user = userService.createUser(request.username(), request.password(), request.roles());
        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    /**
     * Updates an existing user's details. Requires ADMIN role.
     * @param id The user ID to update.
     * @param request The updated user details.
     * @return The updated UserDTO.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing user (Admin only)")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UserRequest request) {
        User user = userService.updateUser(id, request.username(), request.password(), request.roles());
        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    /**
     * Deletes a user by ID. Requires ADMIN role.
     * @param id The user ID to delete.
     * @return 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a user (Admin only)")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    public record UserRequest(String username, String password, Set<Role> roles) {}

    public record ChangePasswordRequest(String newPassword) {}
}
