package dev.phatanon.repository;

import dev.phatanon.entity.CustomCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomCommandRepository extends JpaRepository<CustomCommand, Long> {
    Optional<CustomCommand> findByCommandNameIgnoreCaseAndEnabledTrue(String commandName);
}
