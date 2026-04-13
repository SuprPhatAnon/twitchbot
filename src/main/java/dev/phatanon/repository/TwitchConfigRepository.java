package dev.phatanon.repository;

import dev.phatanon.entity.TwitchConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TwitchConfigRepository extends JpaRepository<TwitchConfig, Long> {
}
