package com.morphcode.ai.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import io.jsonwebtoken.MalformedJwtException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleBadRequest_returns400() {
        ResponseEntity<ApiError> response = handler.handleBadRequest(new BadRequestException("bad input"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("bad input");
    }

    @Test
    void handleResourceNotFound_returns404() {
        ResponseEntity<ApiError> response = handler.handleResourceNotFound(
                new ResourceNotFoundException("Project", "1"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Project").contains("1");
    }

    @Test
    void handleUsernameNotFound_returns404() {
        ResponseEntity<ApiError> response = handler.handleUsernameNotFoundException(
                new UsernameNotFoundException("test@example.com"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("test@example.com");
    }

    @Test
    void handleAuthenticationException_returns401() {
        ResponseEntity<ApiError> response = handler.handleAuthenticationException(
                new BadCredentialsException("wrong password"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Authentication failed");
    }

    @Test
    void handleJwtException_returns401() {
        ResponseEntity<ApiError> response = handler.handleJwtException(
                new MalformedJwtException("bad jwt"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Invalid JWT token");
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ApiError> response = handler.handleAccessDeniedException(
                new AccessDeniedException("forbidden"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Access denied");
    }

    @Test
    void handleInputValidation_returns400WithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "username", "must not be blank");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ApiError> response = handler.handleInputValidationError(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Validation");
    }
}
