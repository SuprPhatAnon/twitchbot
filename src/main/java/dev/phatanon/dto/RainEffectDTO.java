package dev.phatanon.dto;

/**
 * Data Transfer Object for the rain effect on the overlay.
 */
public class RainEffectDTO {
    private String content; // UTF emoji or URL to a picture
    private int duration; // duration in milliseconds

    public RainEffectDTO() {}

    public RainEffectDTO(String content, int duration) {
        this.content = content;
        this.duration = duration;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
}
