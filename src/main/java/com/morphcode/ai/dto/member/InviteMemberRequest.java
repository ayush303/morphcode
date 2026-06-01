package com.morphcode.ai.dto.member;

import com.morphcode.ai.enums.ProjectRole;

public record InviteMemberRequest(
        String email,
        ProjectRole role) {

}
