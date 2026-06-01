package com.morphcode.ai.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.morphcode.ai.dto.project.ProjectResponse;
import com.morphcode.ai.dto.project.ProjectSummaryResponse;
import com.morphcode.ai.entity.Project;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    ProjectResponse toProjectResponse(Project project);

    ProjectSummaryResponse toProjectSummaryResponse(Project project);

    List<ProjectSummaryResponse> toListOfProjectSummaryResponse(List<Project> projects);

}
