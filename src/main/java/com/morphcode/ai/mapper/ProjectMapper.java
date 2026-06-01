package com.morphcode.ai.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.morphcode.ai.dto.project.ProjectResponse;
import com.morphcode.ai.dto.project.ProjectSummaryResponse;
import com.morphcode.ai.entity.Project;
import com.morphcode.ai.enums.ProjectRole;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toProjectResponse(Project project);

    ProjectSummaryResponse toProjectSummaryResponse(Project project, ProjectRole role);

    List<ProjectSummaryResponse> toListOfProjectSummaryResponse(List<Project> projects);

}
