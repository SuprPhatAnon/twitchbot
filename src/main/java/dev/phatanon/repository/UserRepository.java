package dev.phatanon.repository;

import dev.phatanon.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByApiKey(String apiKey);

    Optional<User> findByUsernameAndDeletedFalse(String username);
    Optional<User> findByApiKeyAndDeletedFalse(String apiKey);
    Optional<User> findByIdAndDeletedFalse(Long id);
    java.util.List<User> findAllByDeletedFalse();
}
