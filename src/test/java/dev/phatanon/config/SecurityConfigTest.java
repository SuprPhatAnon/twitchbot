package dev.phatanon.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void overlayHtml_ShouldHaveCorrectCspHeader() throws Exception {
        mockMvc.perform(get("/overlay.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", 
                    org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("https://cdnjs.cloudflare.com"),
                        org.hamcrest.Matchers.containsString("connect-src 'self' ws: wss: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com;"),
                        org.hamcrest.Matchers.containsString("form-action 'self' https://id.twitch.tv")
                    )));
    }

    @Test
    @WithMockUser
    void logoutWithGet_ShouldRedirectToLoginPage() throws Exception {
        mockMvc.perform(get("/api/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }

    @Test
    @WithMockUser
    void logoutWithPost_ShouldRedirectToLoginPage() throws Exception {
        mockMvc.perform(post("/api/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login.html"));
    }
}
