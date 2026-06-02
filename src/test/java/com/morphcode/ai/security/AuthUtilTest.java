package com.morphcode.ai.security;

import com.morphcode.ai.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUtilTest {

    private AuthUtil authUtil;

    // Must be at least 256 bits (32 chars) for HS256
    private static final String SECRET = "test-secret-key-that-is-at-least-32-chars-long!";

    @BeforeEach
    void setUp() {
        authUtil = new AuthUtil();
        ReflectionTestUtils.setField(authUtil, "jwtSecretKey", SECRET);
        SecurityContextHolder.clearContext();
    }

    private User buildUser(Long id, String username) {
        return User.builder().id(id).username(username).password("encoded").name("Test").build();
    }

    @Test
    void generateAccessToken_returnsNonBlankToken() {
        String token = authUtil.generateAccessToken(buildUser(1L, "test@example.com"));
        assertThat(token).isNotBlank();
    }

    @Test
    void verifyAccessToken_returnsCorrectPrincipal() {
        User user = buildUser(42L, "user@example.com");
        String token = authUtil.generateAccessToken(user);

        JwtUserPrincipal principal = authUtil.verifyAccessToken(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("user@example.com");
    }

    @Test
    void verifyAccessToken_invalidToken_throwsException() {
        assertThatThrownBy(() -> authUtil.verifyAccessToken("not.a.valid.jwt"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void getCurrentUserId_withValidPrincipal_returnsUserId() {
        JwtUserPrincipal principal = new JwtUserPrincipal(7L, "alice@example.com", List.of());
        var auth = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThat(authUtil.getCurrentUserId()).isEqualTo(7L);
    }

    @Test
    void getCurrentUserId_withNoAuthentication_throwsException() {
        assertThatThrownBy(() -> authUtil.getCurrentUserId())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void getCurrentUserId_withNonJwtPrincipal_throwsException() {
        var auth = new UsernamePasswordAuthenticationToken("plain-string-principal", null);
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> authUtil.getCurrentUserId())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
