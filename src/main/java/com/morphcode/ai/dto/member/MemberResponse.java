package com.morphcode.ai.dto.member;

import java.time.Instant;

import com.morphcode.ai.enums.ProjectRole;

public record MemberResponse(
        Long userId,
        String email,
        String name,
        String avatarUrl,
        ProjectRole role,
        Instant invitedAt) {

}
