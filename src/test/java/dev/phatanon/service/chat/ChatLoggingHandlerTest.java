package dev.phatanon.service.chat;

import dev.phatanon.model.ChatMessageContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ChatLoggingHandlerTest {

    private ChatLoggingHandler handler;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        handler = new ChatLoggingHandler();
        ReflectionTestUtils.setField(handler, "uploadPath", tempDir.toString());
    }

    @Test
    void testHandle_WritesToFile() throws IOException {
        ChatMessageContext context = ChatMessageContext.builder()
                .message("Hello world")
                .senderName("user123")
                .source("Streamer")
                .build();

        handler.handle(context);

        String dateStr = LocalDate.now().toString();
        Path expectedFile = tempDir.resolve("chat-" + dateStr + ".txt");
        assertTrue(Files.exists(expectedFile), "Log file should exist");

        List<String> lines = Files.readAllLines(expectedFile);
        assertFalse(lines.isEmpty());
        assertTrue(lines.get(0).contains("user123: Hello world"));
        assertTrue(lines.get(0).contains("[Streamer]"));
    }

    @Test
    void testRotationAndGzip() throws IOException {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String yesterdayStr = yesterday.toString();
        Path yesterdayFile = tempDir.resolve("chat-" + yesterdayStr + ".txt");
        Files.write(yesterdayFile, List.of("Yesterday's message"));

        // Set lastRotationDate to yesterday to trigger rotation
        ReflectionTestUtils.setField(handler, "lastRotationDate", yesterday);

        ChatMessageContext context = ChatMessageContext.builder()
                .message("Today's message")
                .senderName("user456")
                .source("Bot")
                .build();

        handler.handle(context);

        // Check that yesterday's file is gzipped and original deleted
        assertFalse(Files.exists(yesterdayFile), "Yesterday's txt file should be deleted");
        Path yesterdayGzip = tempDir.resolve("chat-" + yesterdayStr + ".txt".concat(".gz"));
        assertTrue(Files.exists(yesterdayGzip), "Yesterday's gzip file should exist");

        // Verify gzip content
        try (GZIPInputStream gis = new GZIPInputStream(Files.newInputStream(yesterdayGzip))) {
            byte[] buffer = new byte[1024];
            int len = gis.read(buffer);
            String content = new String(buffer, 0, len);
            assertTrue(content.contains("Yesterday's message"));
        }

        // Check today's file
        String todayStr = LocalDate.now().toString();
        Path todayFile = tempDir.resolve("chat-" + todayStr + ".txt");
        assertTrue(Files.exists(todayFile), "Today's log file should exist");
        List<String> todayLines = Files.readAllLines(todayFile);
        assertTrue(todayLines.get(0).contains("user456: Today's message"));
    }
}
