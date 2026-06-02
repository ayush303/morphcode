package com.morphcode.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private AuthUtil authUtil;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void noAuthorizationHeader_proceedsWithoutAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void bearerTokenMissing_proceedsWithoutAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic somecreds");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validBearerToken_setsAuthentication() throws Exception {
        JwtUserPrincipal principal = new JwtUserPrincipal(1L, "user@example.com", List.of());
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.jwt.token");
        when(authUtil.verifyAccessToken("valid.jwt.token")).thenReturn(principal);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(principal);
    }

    @Test
    void invalidToken_delegatesExceptionToResolver() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad.token");
        when(authUtil.verifyAccessToken("bad.token"))
                .thenThrow(new RuntimeException("invalid jwt"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(handlerExceptionResolver).resolveException(eq(request), eq(response), eq(null), any(RuntimeException.class));
        verify(filterChain, never()).doFilter(any(), any());
    }
}
