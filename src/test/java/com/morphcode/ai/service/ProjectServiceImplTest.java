package com.morphcode.ai.service;

import com.morphcode.ai.dto.project.ProjectRequest;
import com.morphcode.ai.dto.project.ProjectResponse;
import com.morphcode.ai.dto.project.ProjectSummaryResponse;
import com.morphcode.ai.entity.Project;
import com.morphcode.ai.entity.ProjectMember;
import com.morphcode.ai.entity.User;
import com.morphcode.ai.enums.ProjectRole;
import com.morphcode.ai.error.BadRequestException;
import com.morphcode.ai.error.ResourceNotFoundException;
import com.morphcode.ai.mapper.ProjectMapper;
import com.morphcode.ai.repository.ProjectMemberRepository;
import com.morphcode.ai.repository.ProjectRepository;
import com.morphcode.ai.repository.ProjectRepository.ProjectWithRole;
import com.morphcode.ai.repository.UserRepository;
import com.morphcode.ai.security.AuthUtil;
import com.morphcode.ai.service.impl.ProjectServiceImpl;
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
class ProjectServiceImplTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private AuthUtil authUtil;
    @Mock private SubscriptionService subscriptionService;
    @Mock private ProjectTemplateService projectTemplateService;

    @InjectMocks
    private ProjectServiceImpl projectService;

    private Project buildProject() {
        return Project.builder().id(1L).name("My Project").isPublic(false)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private User buildUser() {
        return User.builder().id(10L).username("dev@example.com").build();
    }

    @Test
    void createProject_withinLimit_savesProjectAndMember() {
        ProjectRequest request = new ProjectRequest("My Project");
        Project project = buildProject();
        User owner = buildUser();

        when(subscriptionService.canCreateNewProject()).thenReturn(true);
        when(authUtil.getCurrentUserId()).thenReturn(10L);
        when(userRepository.getReferenceById(10L)).thenReturn(owner);
        when(projectRepository.save(any(Project.class))).thenReturn(project);
        when(projectMemberRepository.save(any(ProjectMember.class))).thenReturn(mock(ProjectMember.class));
        when(projectMapper.toProjectResponse(project))
                .thenReturn(new ProjectResponse(1L, "My Project", Instant.now(), Instant.now(), null));

        ProjectResponse response = projectService.createProject(request);

        assertThat(response.name()).isEqualTo("My Project");
        verify(projectRepository).save(any(Project.class));
        verify(projectMemberRepository).save(any(ProjectMember.class));
        verify(projectTemplateService).initializeProjectFromTemplate(1L);
    }

    @Test
    void createProject_overLimit_throwsBadRequestException() {
        when(subscriptionService.canCreateNewProject()).thenReturn(false);

        assertThatThrownBy(() -> projectService.createProject(new ProjectRequest("X")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Upgrade");

        verify(projectRepository, never()).save(any());
    }

    @Test
    void getUserProjects_returnsAllAccessibleProjects() {
        when(authUtil.getCurrentUserId()).thenReturn(10L);

        ProjectWithRole pwrMock = mock(ProjectWithRole.class);
        when(pwrMock.getProject()).thenReturn(buildProject());
        when(pwrMock.getRole()).thenReturn(ProjectRole.OWNER);
        when(projectRepository.findAllAccessibleByUser(10L)).thenReturn(List.of(pwrMock));
        when(projectMapper.toProjectSummaryResponse(any(Project.class), eq(ProjectRole.OWNER)))
                .thenReturn(new ProjectSummaryResponse(1L, "My Project", ProjectRole.OWNER, Instant.now(), Instant.now()));

        List<ProjectSummaryResponse> result = projectService.getUserProjects();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("My Project");
    }

    @Test
    void getUserProjects_noProjects_returnsEmptyList() {
        when(authUtil.getCurrentUserId()).thenReturn(10L);
        when(projectRepository.findAllAccessibleByUser(10L)).thenReturn(List.of());

        assertThat(projectService.getUserProjects()).isEmpty();
    }

    @Test
    void updateProject_existingProject_updatesName() {
        Project project = buildProject();
        when(authUtil.getCurrentUserId()).thenReturn(10L);
        when(projectRepository.findAccessibleProjectById(1L, 10L)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(projectMapper.toProjectResponse(project))
                .thenReturn(new ProjectResponse(1L, "Renamed", Instant.now(), Instant.now(), null));

        ProjectResponse response = projectService.updateProject(1L, new ProjectRequest("Renamed"));

        assertThat(response.name()).isEqualTo("Renamed");
        verify(projectRepository).save(project);
    }

    @Test
    void updateProject_notFound_throwsResourceNotFoundException() {
        when(authUtil.getCurrentUserId()).thenReturn(10L);
        when(projectRepository.findAccessibleProjectById(99L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.updateProject(99L, new ProjectRequest("X")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void softDelete_existingProject_setsDeletedAt() {
        Project project = buildProject();
        when(authUtil.getCurrentUserId()).thenReturn(10L);
        when(projectRepository.findAccessibleProjectById(1L, 10L)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        projectService.softDelete(1L);

        assertThat(project.getDeletedAt()).isNotNull();
        verify(projectRepository).save(project);
    }
}
