package com.morphcode.ai.enums;

import java.util.Set;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectRole {
    OWNER(Set.of(
            ProjectPermission.VIEW,
            ProjectPermission.EDIT,
            ProjectPermission.DELETE,
            ProjectPermission.VIEW_MEMBERS,
            ProjectPermission.MANAGE_MEMBERS)),
    EDITOR(Set.of(
            ProjectPermission.VIEW,
            ProjectPermission.EDIT,
            ProjectPermission.VIEW_MEMBERS)),
    VIEWER(Set.of(
            ProjectPermission.VIEW,
            ProjectPermission.VIEW_MEMBERS));

    private final Set<ProjectPermission> permissions;
}
