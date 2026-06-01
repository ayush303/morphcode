package com.morphcode.ai.service;

import java.util.List;

import com.morphcode.ai.dto.project.ProjectRequest;
import com.morphcode.ai.dto.project.ProjectResponse;
import com.morphcode.ai.dto.project.ProjectSummaryResponse;

public interface ProjectService {

    List<ProjectSummaryResponse> getUserProjects();

    ProjectSummaryResponse getUserProjectById(Long id);

    ProjectResponse createProject(ProjectRequest request);

    ProjectResponse updateProject(Long id, ProjectRequest request);

    void softDelete(Long id);
}
