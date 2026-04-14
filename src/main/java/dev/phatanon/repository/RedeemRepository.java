package dev.phatanon.repository;

import dev.phatanon.entity.Redeem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository interface for {@link Redeem} entity.
 */
@Repository
public interface RedeemRepository extends JpaRepository<Redeem, Long> {
    /**
     * Finds a redeem by its title.
     * @param title The title of the channel point redeem.
     * @return An Optional containing the redeem if found.
     */
    Optional<Redeem> findByTitle(String title);
}
