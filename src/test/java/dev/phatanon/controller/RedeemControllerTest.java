package dev.phatanon.controller;

import dev.phatanon.entity.Redeem;
import dev.phatanon.repository.RedeemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class RedeemControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RedeemRepository redeemRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private RedeemController redeemController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(redeemController).build();
    }

    @Test
    void shouldGetAllRedeems() throws Exception {
        Redeem r1 = new Redeem();
        r1.setId(1L);
        r1.setTitle("Redeem 1");
        
        Redeem r2 = new Redeem();
        r2.setId(2L);
        r2.setTitle("Redeem 2");

        when(redeemRepository.findAll()).thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/api/redeems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Redeem 1")))
                .andExpect(jsonPath("$[1].title", is("Redeem 2")));
    }

    @Test
    void shouldAddRedeem() throws Exception {
        Redeem redeem = new Redeem();
        redeem.setTitle("New Redeem");
        
        Redeem savedRedeem = new Redeem();
        savedRedeem.setId(1L);
        savedRedeem.setTitle("New Redeem");

        when(redeemRepository.save(any(Redeem.class))).thenReturn(savedRedeem);

        mockMvc.perform(post("/api/redeems")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"New Redeem\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.title", is("New Redeem")));

        verify(messagingTemplate).convertAndSend(eq("/topic/redeems-list"), eq("refresh"));
    }

    @Test
    void shouldDeleteRedeem() throws Exception {
        Redeem redeem = new Redeem();
        redeem.setId(1L);
        redeem.setTitle("To Delete");

        when(redeemRepository.findById(1L)).thenReturn(Optional.of(redeem));

        mockMvc.perform(delete("/api/redeems/1"))
                .andExpect(status().isNoContent());

        verify(redeemRepository).delete(redeem);
        verify(messagingTemplate).convertAndSend(eq("/topic/redeems-list"), eq("refresh"));
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentRedeem() throws Exception {
        when(redeemRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/redeems/1"))
                .andExpect(status().isNotFound());
    }
}
