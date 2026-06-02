package com.morphcode.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morphcode.ai.dto.project.ProjectRequest;
import com.morphcode.ai.dto.project.ProjectResponse;
import com.morphcode.ai.dto.project.ProjectSummaryResponse;
import com.morphcode.ai.enums.ProjectRole;
import com.morphcode.ai.error.BadRequestException;
import com.morphcode.ai.error.ResourceNotFoundException;
import com.morphcode.ai.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ProjectController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*WebSecurityConfig"),
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = ".*JwtAuthFilter")
        }
)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    private ProjectSummaryResponse buildSummary(Long id, String name) {
        return new ProjectSummaryResponse(id, name, ProjectRole.OWNER, Instant.now(), Instant.now());
    }

    private ProjectResponse buildResponse(Long id, String name) {
        return new ProjectResponse(id, name, Instant.now(), Instant.now(), null);
    }

    @Test
    void getMyProjects_returnsListOf200() throws Exception {
        when(projectService.getUserProjects())
                .thenReturn(List.of(buildSummary(1L, "Alpha"), buildSummary(2L, "Beta")));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[1].name").value("Beta"));
    }

    @Test
    void getMyProjects_empty_returns200EmptyArray() throws Exception {
        when(projectService.getUserProjects()).thenReturn(List.of());

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getProjectById_existingId_returns200() throws Exception {
        when(projectService.getUserProjectById(1L)).thenReturn(buildSummary(1L, "Alpha"));

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Alpha"));
    }

    @Test
    void getProjectById_notFound_returns400() throws Exception {
        when(projectService.getUserProjectById(99L))
                .thenThrow(new BadRequestException("Project Not Found"));

        mockMvc.perform(get("/api/projects/99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Project Not Found"));
    }

    @Test
    void createProject_validRequest_returns201() throws Exception {
        ProjectRequest request = new ProjectRequest("New Project");
        when(projectService.createProject(any(ProjectRequest.class)))
                .thenReturn(buildResponse(1L, "New Project"));

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("New Project"));
    }

    @Test
    void createProject_blankName_returns400() throws Exception {
        ProjectRequest request = new ProjectRequest("");

        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProject_validRequest_returns200() throws Exception {
        ProjectRequest request = new ProjectRequest("Renamed");
        when(projectService.updateProject(eq(1L), any(ProjectRequest.class)))
                .thenReturn(buildResponse(1L, "Renamed"));

        mockMvc.perform(patch("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void updateProject_notFound_returns404() throws Exception {
        ProjectRequest request = new ProjectRequest("X");
        when(projectService.updateProject(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Project", "99"));

        mockMvc.perform(patch("/api/projects/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteProject_existingId_returns204() throws Exception {
        doNothing().when(projectService).softDelete(1L);

        mockMvc.perform(delete("/api/projects/1"))
                .andExpect(status().isNoContent());

        verify(projectService).softDelete(1L);
    }
}
