package dev.phatanon.controller;

import dev.phatanon.entity.Role;
import dev.phatanon.entity.User;
import dev.phatanon.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UserDetails adminUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        adminUser = new org.springframework.security.core.userdetails.User(
                "admin", "pass", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType().equals(UserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                                  org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                                  org.springframework.web.context.request.NativeWebRequest webRequest,
                                                  org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return adminUser;
                    }
                })
                .build();
    }

    @Test
    void getCurrentUser_ReturnsUser() throws Exception {
        User user = new User("admin", "pass", Set.of(Role.ROLE_ADMIN));
        when(userService.findByUsername("admin")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("admin")));
    }

    @Test
    void changePassword_CallsService() throws Exception {
        mockMvc.perform(post("/api/users/me/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\": \"newpass\"}"))
                .andExpect(status().isOk());

        verify(userService).changePassword("admin", "newpass");
    }

    @Test
    void getAllUsers_ReturnsList() throws Exception {
        User user = new User("admin", "pass", Set.of(Role.ROLE_ADMIN));
        when(userService.findAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username", is("admin")));
    }

    @Test
    void createUser_ValidRequest_ReturnsUser() throws Exception {
        User user = new User("newuser", "pass", Set.of(Role.ROLE_UPLOAD));
        when(userService.existsByUsername("newuser")).thenReturn(false);
        when(userService.createUser(eq("newuser"), eq("pass"), anySet())).thenReturn(user);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"newuser\", \"password\": \"pass\", \"roles\": [\"ROLE_UPLOAD\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("newuser")));
    }

    @Test
    void createUser_UsernameExists_ReturnsBadRequest() throws Exception {
        when(userService.existsByUsername("existing")).thenReturn(true);

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"existing\", \"password\": \"pass\", \"roles\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Username already exists"));
    }

    @Test
    void updateUser_ValidRequest_ReturnsUpdatedUser() throws Exception {
        User user = new User("updated", "pass", Set.of(Role.ROLE_ADMIN));
        user.setId(1L);
        when(userService.updateUser(eq(1L), anyString(), anyString(), anySet())).thenReturn(user);

        mockMvc.perform(put("/api/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"updated\", \"password\": \"newpass\", \"roles\": [\"ROLE_ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("updated")));
    }

    @Test
    void deleteUser_CallsService() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());

        verify(userService).deleteUser(1L);
    }
    @Test
    void rotateApiKey_ReturnsUserWithNewKey() throws Exception {
        User user = new User("admin", "pass", Set.of(Role.ROLE_ADMIN));
        user.setApiKey("new-api-key");
        when(userService.rotateApiKey("admin")).thenReturn(user);

        mockMvc.perform(post("/api/users/me/rotate-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.apiKey", is("new-api-key")));
    }
}
