package com.morphcode.ai.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRoleTest {

    @Test
    void owner_hasAllPermissions() {
        assertThat(ProjectRole.OWNER.getPermissions()).containsExactlyInAnyOrder(
                ProjectPermission.VIEW,
                ProjectPermission.EDIT,
                ProjectPermission.DELETE,
                ProjectPermission.VIEW_MEMBERS,
                ProjectPermission.MANAGE_MEMBERS
        );
    }

    @Test
    void editor_hasViewEditAndViewMembersOnly() {
        assertThat(ProjectRole.EDITOR.getPermissions()).containsExactlyInAnyOrder(
                ProjectPermission.VIEW,
                ProjectPermission.EDIT,
                ProjectPermission.VIEW_MEMBERS
        );
        assertThat(ProjectRole.EDITOR.getPermissions()).doesNotContain(
                ProjectPermission.DELETE,
                ProjectPermission.MANAGE_MEMBERS
        );
    }

    @Test
    void viewer_hasOnlyViewAndViewMembers() {
        assertThat(ProjectRole.VIEWER.getPermissions()).containsExactlyInAnyOrder(
                ProjectPermission.VIEW,
                ProjectPermission.VIEW_MEMBERS
        );
        assertThat(ProjectRole.VIEWER.getPermissions()).doesNotContain(
                ProjectPermission.EDIT,
                ProjectPermission.DELETE,
                ProjectPermission.MANAGE_MEMBERS
        );
    }

    @Test
    void owner_canEdit() {
        assertThat(ProjectRole.OWNER.getPermissions()).contains(ProjectPermission.EDIT);
    }

    @Test
    void viewer_cannotEdit() {
        assertThat(ProjectRole.VIEWER.getPermissions()).doesNotContain(ProjectPermission.EDIT);
    }

    @Test
    void projectPermission_valuesAreCorrect() {
        assertThat(ProjectPermission.VIEW.getValue()).isEqualTo("project:view");
        assertThat(ProjectPermission.EDIT.getValue()).isEqualTo("project:edit");
        assertThat(ProjectPermission.DELETE.getValue()).isEqualTo("project:delete");
        assertThat(ProjectPermission.MANAGE_MEMBERS.getValue()).isEqualTo("project_members:manage");
        assertThat(ProjectPermission.VIEW_MEMBERS.getValue()).isEqualTo("project_members:view");
    }
}
