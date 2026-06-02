package com.morphcode.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morphcode.ai.dto.auth.AuthResponse;
import com.morphcode.ai.dto.auth.LoginRequest;
import com.morphcode.ai.dto.auth.SignupRequest;
import com.morphcode.ai.dto.auth.UserProfileResponse;
import com.morphcode.ai.error.BadRequestException;
import com.morphcode.ai.service.AuthService;
import com.morphcode.ai.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*WebSecurityConfig"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*JwtAuthFilter")
        }
)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    private AuthResponse buildAuthResponse() {
        return new AuthResponse("jwt-token", new UserProfileResponse(1L, "test@example.com", "Test", null));
    }

    @Test
    void signup_validRequest_returns200WithToken() throws Exception {
        SignupRequest request = new SignupRequest("test@example.com", "Test User", "password123");
        when(authService.signup(any(SignupRequest.class))).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    void signup_duplicateUsername_returns400() throws Exception {
        SignupRequest request = new SignupRequest("existing@example.com", "User", "pass1234");
        when(authService.signup(any())).thenThrow(new BadRequestException("User already exists"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User already exists"));
    }

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        when(authService.login(any(LoginRequest.class))).thenReturn(buildAuthResponse());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void getProfile_returns200WithProfile() throws Exception {
        UserProfileResponse profile = new UserProfileResponse(1L, "test@example.com", "Test", null);
        when(userService.getProfile(1L)).thenReturn(profile);

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }
}
