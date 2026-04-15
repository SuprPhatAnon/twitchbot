package dev.phatanon.controller;

import dev.phatanon.entity.Redeem;
import dev.phatanon.repository.RedeemRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing {@link Redeem} entities.
 */
@RestController
@RequestMapping("/api/redeems")
@Tag(name = "Redeem Management", description = "Endpoints for managing Twitch channel point redeems")
public class RedeemController {

    private final RedeemRepository redeemRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public RedeemController(RedeemRepository redeemRepository, SimpMessagingTemplate messagingTemplate) {
        this.redeemRepository = redeemRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Retrieves all defined Twitch channel point redeems from the {@link RedeemRepository}.
     * @return A list of all {@link Redeem} entities.
     */
    @GetMapping
    @Operation(summary = "Get all redeems")
    public List<Redeem> getAllRedeems() {
        return redeemRepository.findAll();
    }

    /**
     * Adds a new redeem title to the system and broadcasts a refresh message to WebSocket subscribers.
     * Requires an API key for authorization.
     * @param redeem The {@link Redeem} entity to create.
     * @return The saved {@link Redeem} entity.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a new redeem")
    public Redeem addRedeem(@RequestBody Redeem redeem) {
        Redeem savedRedeem = redeemRepository.save(redeem);
        messagingTemplate.convertAndSend("/topic/redeems-list", "refresh");
        return savedRedeem;
    }

    /**
     * Deletes a redeem from the system and broadcasts a refresh message to WebSocket subscribers.
     * Requires an API key for authorization.
     * @param id The ID of the redeem to delete.
     * @return 204 No Content if successful, or 404 Not Found if the ID does not exist.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a redeem")
    public ResponseEntity<Void> deleteRedeem(@PathVariable Long id) {
        return redeemRepository.findById(id)
                .map(redeem -> {
                    redeemRepository.delete(redeem);
                    messagingTemplate.convertAndSend("/topic/redeems-list", "refresh");
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
