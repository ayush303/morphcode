package com.morphcode.ai.dto.auth;

public record AuthResponse(
        String token,
        UserProfileResponse user) {
}
