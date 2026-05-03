package dev.phatanon.service.chat;

import dev.phatanon.model.ChatMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPOutputStream;

/**
 * Handler that logs all chat messages to a daily rotated and gzipped text file
 * in the songs folder.
 */
@Service
public class ChatLoggingHandler implements ChatMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatLoggingHandler.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${twitch.song-upload-path}")
    private String uploadPath;

    private LocalDate lastRotationDate;

    @Override
    public boolean canHandle(ChatMessageContext context) {
        return true; // Handle all messages
    }

    @Override
    public void handle(ChatMessageContext context) {
        try {
            rotateIfNecessary();
            writeMessage(context);
        } catch (Exception e) {
            log.error("Failed to log chat message: {}", e.getMessage(), e);
        }
    }

    private synchronized void rotateIfNecessary() {
        LocalDate today = LocalDate.now();
        if (lastRotationDate == null) {
            lastRotationDate = today;
            return;
        }

        if (today.isAfter(lastRotationDate)) {
            log.info("Rotating chat log for date: {}", lastRotationDate);
            gzipPreviousLog(lastRotationDate);
            lastRotationDate = today;
        }
    }

    private void gzipPreviousLog(LocalDate date) {
        String fileName = "chat-" + date.format(DATE_FORMATTER) + ".txt";
        Path filePath = Paths.get(uploadPath, fileName);
        if (Files.exists(filePath)) {
            Path gzipPath = Paths.get(uploadPath, fileName + ".gz");
            try (GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(gzipPath.toFile()));
                 ) {
                Files.copy(filePath, gzos);
                gzos.finish();
                Files.delete(filePath);
                log.info("Successfully gzipped chat log: {}", gzipPath);
            } catch (Exception e) {
                log.error("Failed to gzip chat log {}: {}", filePath, e.getMessage(), e);
            }
        }
    }

    private synchronized void writeMessage(ChatMessageContext context) {
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        String fileName = "chat-" + dateStr + ".txt";
        Path filePath = Paths.get(uploadPath, fileName);

        try {
            File dir = new File(uploadPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
            String logLine = String.format("[%s] [%s] %s: %s%n", 
                timestamp, context.getSource(), context.getSenderName(), context.getMessage());

            Files.write(filePath, logLine.getBytes(StandardCharsets.UTF_8), 
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.error("Error writing to chat log {}: {}", filePath, e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 100; // Log after other handlers potentially process it
    }
}
