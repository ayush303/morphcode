package com.morphcode.ai.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.morphcode.ai.dto.project.ProjectRequest;
import com.morphcode.ai.dto.project.ProjectResponse;
import com.morphcode.ai.dto.project.ProjectSummaryResponse;
import com.morphcode.ai.entity.Project;
import com.morphcode.ai.entity.User;
import com.morphcode.ai.mapper.ProjectMapper;
import com.morphcode.ai.repository.ProjectRepository;
import com.morphcode.ai.repository.UserRepository;
import com.morphcode.ai.service.ProjectService;

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

    @Override
    public ProjectResponse createProject(ProjectRequest request, Long userId) {

        User owner = userRepository.findById(userId).orElseThrow();

        Project project = Project.builder()
                .name(request.name())
                .owner(owner)
                .isPublic(false)
                .build();

        project = projectRepository.save(project);
        return projectMapper.toProjectResponse(project);
    }

    @Override
    public List<ProjectSummaryResponse> getUserProjects(Long userId) {

        // return projectRepository.findAllAccessibleByUser(userId)
        // .stream()
        // .map(projectMapper::toProjectSummaryResponse)
        // .collect(Collectors.toList());

        var projects = projectRepository.findAllAccessibleByUser(userId);
        return projectMapper.toListOfProjectSummaryResponse(projects);
    }

    @Override
    public ProjectResponse getUserProjectById(Long id, Long userId) {
        return null;
    }

    @Override
    public ProjectResponse updateProject(Long id, ProjectRequest request, Long userId) {
        return null;
    }

    @Override
    public void softDelete(Long id, Long userId) {

    }
}
