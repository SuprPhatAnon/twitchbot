package dev.phatanon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for the rain effect on the overlay.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RainEffectDTO {
    private String content; // UTF emoji or URL to a picture
    private int duration; // duration in milliseconds
}
