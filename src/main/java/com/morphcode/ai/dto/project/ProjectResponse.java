package com.morphcode.ai.dto.project;

import java.time.Instant;

import com.morphcode.ai.dto.auth.UserProfileResponse;

public record ProjectResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt,
        UserProfileResponse owner) {

}
