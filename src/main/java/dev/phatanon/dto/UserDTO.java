package dev.phatanon.dto;

import dev.phatanon.entity.Role;
import dev.phatanon.entity.User;
import java.util.Set;

public record UserDTO(
    Long id,
    String username,
    Set<Role> roles,
    String apiKey // This will still be returned, but maybe masked or only for /me
) {
    public static UserDTO fromEntity(User user) {
        return new UserDTO(
            user.getId(),
            user.getUsername(),
            user.getRoles(),
            user.getApiKey()
        );
    }
}
