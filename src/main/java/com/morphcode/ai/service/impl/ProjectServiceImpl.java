package com.morphcode.ai.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.morphcode.ai.dto.project.ProjectRequest;
import com.morphcode.ai.dto.project.ProjectResponse;
import com.morphcode.ai.dto.project.ProjectSummaryResponse;
import com.morphcode.ai.entity.Project;
import com.morphcode.ai.entity.ProjectMember;
import com.morphcode.ai.entity.ProjectMemberId;
import com.morphcode.ai.entity.User;
import com.morphcode.ai.enums.ProjectRole;
import com.morphcode.ai.error.BadRequestException;
import com.morphcode.ai.error.ResourceNotFoundException;
import com.morphcode.ai.mapper.ProjectMapper;
import com.morphcode.ai.repository.ProjectMemberRepository;
import com.morphcode.ai.repository.ProjectRepository;
import com.morphcode.ai.repository.UserRepository;
import com.morphcode.ai.security.AuthUtil;
import com.morphcode.ai.service.ProjectService;
import com.morphcode.ai.service.ProjectTemplateService;
import com.morphcode.ai.service.SubscriptionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Transactional
public class ProjectServiceImpl implements ProjectService {

    ProjectRepository projectRepository;
    UserRepository userRepository;
    ProjectMapper projectMapper;
    ProjectMemberRepository projectMemberRepository;
    AuthUtil authUtil;
    SubscriptionService subscriptionService;
    ProjectTemplateService projectTemplateService;

    @Override
    public ProjectResponse createProject(ProjectRequest request) {

        if (!subscriptionService.canCreateNewProject()) {
            throw new BadRequestException("User cannot create a New project with current Plan, Upgrade plan now.");
        }

        Long userId = authUtil.getCurrentUserId();
        // User owner = userRepository.findById(userId).orElseThrow(
        // () -> new ResourceNotFoundException("User", userId.toString())
        // );
        User owner = userRepository.getReferenceById(userId);

        Project project = Project.builder()
                .name(request.name())
                .isPublic(false)
                .build();
        project = projectRepository.save(project);

        ProjectMemberId projectMemberId = new ProjectMemberId(project.getId(), owner.getId());
        ProjectMember projectMember = ProjectMember.builder()
                .id(projectMemberId)
                .projectRole(ProjectRole.OWNER)
                .user(owner)
                .acceptedAt(Instant.now())
                .invitedAt(Instant.now())
                .project(project)
                .build();
        projectMemberRepository.save(projectMember);

        projectTemplateService.initializeProjectFromTemplate(project.getId());

        return projectMapper.toProjectResponse(project);
    }

    @Override
    public List<ProjectSummaryResponse> getUserProjects() {
        Long userId = authUtil.getCurrentUserId();
        var projectsWithRoles = projectRepository.findAllAccessibleByUser(userId);
        return projectsWithRoles.stream()
                .map(p -> projectMapper.toProjectSummaryResponse(p.getProject(), p.getRole()))
                .toList();
    }

    @Override
    @PreAuthorize("@security.canViewProject(#projectId)")
    public ProjectSummaryResponse getUserProjectById(Long projectId) {
        Long userId = authUtil.getCurrentUserId();

        var projectWithRole = projectRepository.findAccessibleProjectByIdWithRole(projectId, userId)
                .orElseThrow(() -> new BadRequestException("Project Not Found"));

        return projectMapper.toProjectSummaryResponse(projectWithRole.getProject(), projectWithRole.getRole());
    }

    @Override
    @PreAuthorize("@security.canEditProject(#projectId)")
    public ProjectResponse updateProject(Long projectId, ProjectRequest request) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);

        project.setName(request.name());
        project = projectRepository.save(project);

        return projectMapper.toProjectResponse(project);
    }

    @Override
    @PreAuthorize("@security.canDeleteProject(#projectId)")
    public void softDelete(Long projectId) {
        Long userId = authUtil.getCurrentUserId();
        Project project = getAccessibleProjectById(projectId, userId);

        project.setDeletedAt(Instant.now());
        projectRepository.save(project);
    }

    /// INTERNAL FUNCTIONS

    public Project getAccessibleProjectById(Long projectId, Long userId) {
        return projectRepository.findAccessibleProjectById(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
    }
}
