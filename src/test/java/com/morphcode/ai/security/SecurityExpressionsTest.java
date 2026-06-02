package com.morphcode.ai.security;

import com.morphcode.ai.enums.ProjectPermission;
import com.morphcode.ai.enums.ProjectRole;
import com.morphcode.ai.repository.ProjectMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityExpressionsTest {

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private AuthUtil authUtil;

    @InjectMocks
    private SecurityExpressions securityExpressions;

    private static final Long PROJECT_ID = 1L;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        when(authUtil.getCurrentUserId()).thenReturn(USER_ID);
    }

    @Test
    void canViewProject_ownerRole_returnsTrue() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.OWNER));

        assertThat(securityExpressions.canViewProject(PROJECT_ID)).isTrue();
    }

    @Test
    void canViewProject_viewerRole_returnsTrue() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.VIEWER));

        assertThat(securityExpressions.canViewProject(PROJECT_ID)).isTrue();
    }

    @Test
    void canViewProject_notMember_returnsFalse() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThat(securityExpressions.canViewProject(PROJECT_ID)).isFalse();
    }

    @Test
    void canEditProject_ownerRole_returnsTrue() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.OWNER));

        assertThat(securityExpressions.canEditProject(PROJECT_ID)).isTrue();
    }

    @Test
    void canEditProject_viewerRole_returnsFalse() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.VIEWER));

        assertThat(securityExpressions.canEditProject(PROJECT_ID)).isFalse();
    }

    @Test
    void canDeleteProject_ownerRole_returnsTrue() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.OWNER));

        assertThat(securityExpressions.canDeleteProject(PROJECT_ID)).isTrue();
    }

    @Test
    void canDeleteProject_editorRole_returnsFalse() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.EDITOR));

        assertThat(securityExpressions.canDeleteProject(PROJECT_ID)).isFalse();
    }

    @Test
    void canManageMembers_ownerRole_returnsTrue() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.OWNER));

        assertThat(securityExpressions.canManageMembers(PROJECT_ID)).isTrue();
    }

    @Test
    void canManageMembers_editorRole_returnsFalse() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.EDITOR));

        assertThat(securityExpressions.canManageMembers(PROJECT_ID)).isFalse();
    }

    @Test
    void canViewMembers_editorRole_returnsTrue() {
        when(projectMemberRepository.findRoleByProjectIdAndUserId(PROJECT_ID, USER_ID))
                .thenReturn(Optional.of(ProjectRole.EDITOR));

        assertThat(securityExpressions.canViewMembers(PROJECT_ID)).isTrue();
    }
}
