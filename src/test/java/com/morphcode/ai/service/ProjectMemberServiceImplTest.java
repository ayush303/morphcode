package com.morphcode.ai.service;

import com.morphcode.ai.dto.member.InviteMemberRequest;
import com.morphcode.ai.dto.member.MemberResponse;
import com.morphcode.ai.dto.member.UpdateMemberRoleRequest;
import com.morphcode.ai.entity.Project;
import com.morphcode.ai.entity.ProjectMember;
import com.morphcode.ai.entity.ProjectMemberId;
import com.morphcode.ai.entity.User;
import com.morphcode.ai.enums.ProjectRole;
import com.morphcode.ai.mapper.ProjectMemberMapper;
import com.morphcode.ai.repository.ProjectMemberRepository;
import com.morphcode.ai.repository.ProjectRepository;
import com.morphcode.ai.repository.UserRepository;
import com.morphcode.ai.security.AuthUtil;
import com.morphcode.ai.service.impl.ProjectMemberServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectMemberServiceImplTest {

    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberMapper projectMemberMapper;
    @Mock private UserRepository userRepository;
    @Mock private AuthUtil authUtil;

    @InjectMocks
    private ProjectMemberServiceImpl projectMemberService;

    private static final Long PROJECT_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;

    private Project buildProject() {
        return Project.builder().id(PROJECT_ID).name("Test").build();
    }

    private User buildUser(Long id, String username) {
        return User.builder().id(id).username(username).build();
    }

    private MemberResponse buildMemberResponse(Long userId, ProjectRole role) {
        return new MemberResponse(userId, "user@example.com", "User", null, role, Instant.now());
    }

    @Test
    void getProjectMembers_returnsAllMembers() {
        ProjectMember member = ProjectMember.builder()
                .id(new ProjectMemberId(PROJECT_ID, MEMBER_ID))
                .build();
        when(projectMemberRepository.findByIdProjectId(PROJECT_ID)).thenReturn(List.of(member));
        when(projectMemberMapper.toProjectMemberResponseFromMember(member))
                .thenReturn(buildMemberResponse(MEMBER_ID, ProjectRole.EDITOR));

        List<MemberResponse> result = projectMemberService.getProjectMembers(PROJECT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(MEMBER_ID);
    }

    @Test
    void inviteMember_newUser_savesAndReturnsMember() {
        InviteMemberRequest request = new InviteMemberRequest("member@example.com", ProjectRole.EDITOR);
        Project project = buildProject();
        User invitee = buildUser(MEMBER_ID, "member@example.com");

        when(authUtil.getCurrentUserId()).thenReturn(OWNER_ID);
        when(projectRepository.findAccessibleProjectById(PROJECT_ID, OWNER_ID)).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("member@example.com")).thenReturn(Optional.of(invitee));
        when(projectMemberRepository.existsById(any(ProjectMemberId.class))).thenReturn(false);
        when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(i -> i.getArgument(0));
        when(projectMemberMapper.toProjectMemberResponseFromMember(any()))
                .thenReturn(buildMemberResponse(MEMBER_ID, ProjectRole.EDITOR));

        MemberResponse response = projectMemberService.inviteMember(PROJECT_ID, request);

        assertThat(response.userId()).isEqualTo(MEMBER_ID);
        assertThat(response.role()).isEqualTo(ProjectRole.EDITOR);
        verify(projectMemberRepository).save(any(ProjectMember.class));
    }

    @Test
    void inviteMember_selfInvite_throwsRuntimeException() {
        InviteMemberRequest request = new InviteMemberRequest("owner@example.com", ProjectRole.EDITOR);
        Project project = buildProject();
        User invitee = buildUser(OWNER_ID, "owner@example.com");

        when(authUtil.getCurrentUserId()).thenReturn(OWNER_ID);
        when(projectRepository.findAccessibleProjectById(PROJECT_ID, OWNER_ID)).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("owner@example.com")).thenReturn(Optional.of(invitee));

        assertThatThrownBy(() -> projectMemberService.inviteMember(PROJECT_ID, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void inviteMember_alreadyExists_throwsRuntimeException() {
        InviteMemberRequest request = new InviteMemberRequest("member@example.com", ProjectRole.VIEWER);
        Project project = buildProject();
        User invitee = buildUser(MEMBER_ID, "member@example.com");

        when(authUtil.getCurrentUserId()).thenReturn(OWNER_ID);
        when(projectRepository.findAccessibleProjectById(PROJECT_ID, OWNER_ID)).thenReturn(Optional.of(project));
        when(userRepository.findByUsername("member@example.com")).thenReturn(Optional.of(invitee));
        when(projectMemberRepository.existsById(any(ProjectMemberId.class))).thenReturn(true);

        assertThatThrownBy(() -> projectMemberService.inviteMember(PROJECT_ID, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("invite once again");
    }

    @Test
    void updateMemberRole_existingMember_updatesRole() {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest(ProjectRole.VIEWER);
        Project project = buildProject();
        ProjectMember member = ProjectMember.builder()
                .id(new ProjectMemberId(PROJECT_ID, MEMBER_ID))
                .projectRole(ProjectRole.EDITOR)
                .build();

        when(authUtil.getCurrentUserId()).thenReturn(OWNER_ID);
        when(projectRepository.findAccessibleProjectById(PROJECT_ID, OWNER_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findById(any(ProjectMemberId.class)))
                .thenReturn(Optional.of(member));
        when(projectMemberRepository.save(member)).thenReturn(member);
        when(projectMemberMapper.toProjectMemberResponseFromMember(member))
                .thenReturn(buildMemberResponse(MEMBER_ID, ProjectRole.VIEWER));

        MemberResponse response = projectMemberService.updateMemberRole(PROJECT_ID, MEMBER_ID, request);

        assertThat(response.role()).isEqualTo(ProjectRole.VIEWER);
        assertThat(member.getProjectRole()).isEqualTo(ProjectRole.VIEWER);
        verify(projectMemberRepository).save(member);
    }

    @Test
    void removeProjectMember_existingMember_deletesById() {
        Project project = buildProject();
        ProjectMemberId memberId = new ProjectMemberId(PROJECT_ID, MEMBER_ID);

        when(authUtil.getCurrentUserId()).thenReturn(OWNER_ID);
        when(projectRepository.findAccessibleProjectById(PROJECT_ID, OWNER_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsById(any(ProjectMemberId.class))).thenReturn(true);

        projectMemberService.removeProjectMember(PROJECT_ID, MEMBER_ID);

        verify(projectMemberRepository).deleteById(any(ProjectMemberId.class));
    }

    @Test
    void removeProjectMember_notFound_throwsRuntimeException() {
        Project project = buildProject();

        when(authUtil.getCurrentUserId()).thenReturn(OWNER_ID);
        when(projectRepository.findAccessibleProjectById(PROJECT_ID, OWNER_ID)).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsById(any(ProjectMemberId.class))).thenReturn(false);

        assertThatThrownBy(() -> projectMemberService.removeProjectMember(PROJECT_ID, MEMBER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }
}
