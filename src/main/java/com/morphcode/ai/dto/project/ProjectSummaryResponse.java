package com.morphcode.ai.dto.project;

import java.time.Instant;

import com.morphcode.ai.enums.ProjectRole;

public record ProjectSummaryResponse(
        Long id,
        String name,
        ProjectRole role,
        Instant createdAt,
        Instant updatedAt) {

}
