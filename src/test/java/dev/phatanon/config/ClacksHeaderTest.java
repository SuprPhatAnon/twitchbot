package dev.phatanon.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ClacksHeaderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void overlayHtmlShouldContainClacksHeader() throws Exception {
        mockMvc.perform(get("/overlay.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Clacks-Overhead", "GNU Terry Pratchett"));
    }

    @Test
    void apiConfigShouldContainClacksHeader() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(header().string("X-Clacks-Overhead", "GNU Terry Pratchett"));
    }
}
