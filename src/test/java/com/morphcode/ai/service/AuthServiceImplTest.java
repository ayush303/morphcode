package com.morphcode.ai.service;

import com.morphcode.ai.dto.auth.AuthResponse;
import com.morphcode.ai.dto.auth.LoginRequest;
import com.morphcode.ai.dto.auth.SignupRequest;
import com.morphcode.ai.dto.auth.UserProfileResponse;
import com.morphcode.ai.entity.User;
import com.morphcode.ai.error.BadRequestException;
import com.morphcode.ai.mapper.UserMapper;
import com.morphcode.ai.repository.UserRepository;
import com.morphcode.ai.security.AuthUtil;
import com.morphcode.ai.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthUtil authUtil;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    private User buildUser() {
        return User.builder().id(1L).username("test@example.com").name("Test User").password("encoded").build();
    }

    private UserProfileResponse buildProfile() {
        return new UserProfileResponse(1L, "test@example.com", "Test User", null);
    }

    @Test
    void signup_newUser_savesAndReturnsToken() {
        SignupRequest request = new SignupRequest("test@example.com", "Test User", "password123");
        User user = buildUser();

        when(userRepository.findByUsername("test@example.com")).thenReturn(Optional.empty());
        when(userMapper.toEntity(request)).thenReturn(user);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(user)).thenReturn(user);
        when(authUtil.generateAccessToken(user)).thenReturn("jwt-token");
        when(userMapper.toUserProfileResponse(user)).thenReturn(buildProfile());

        AuthResponse response = authService.signup(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(1L);
        verify(userRepository).save(user);
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void signup_existingUsername_throwsBadRequestException() {
        SignupRequest request = new SignupRequest("existing@example.com", "User", "pass1234");
        when(userRepository.findByUsername("existing@example.com"))
                .thenReturn(Optional.of(buildUser()));

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("existing@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_returnsToken() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");
        User user = buildUser();

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(authUtil.generateAccessToken(user)).thenReturn("jwt-token");
        when(userMapper.toUserProfileResponse(user)).thenReturn(buildProfile());

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(1L);
    }

    @Test
    void login_invalidCredentials_propagatesAuthenticationException() {
        LoginRequest request = new LoginRequest("test@example.com", "wrongpass");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }
}
