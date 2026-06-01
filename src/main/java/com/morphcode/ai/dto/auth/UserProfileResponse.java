package com.morphcode.ai.dto.auth;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String avatarUrl) {

}
