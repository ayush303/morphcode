package com.morphcode.ai.dto.member;

import com.morphcode.ai.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
                @NotNull ProjectRole role) {

}
