package com.morphcode.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morphcode.ai.dto.member.InviteMemberRequest;
import com.morphcode.ai.dto.member.MemberResponse;
import com.morphcode.ai.dto.member.UpdateMemberRoleRequest;
import com.morphcode.ai.enums.ProjectRole;
import com.morphcode.ai.service.ProjectMemberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
        controllers = ProjectMemberController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ProjectMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectMemberService projectMemberService;

    private MemberResponse buildMember(Long userId, String email, ProjectRole role) {
        return new MemberResponse(userId, email, "User", null, role, Instant.now());
    }

    @Test
    void getProjectMembers_returns200WithList() throws Exception {
        when(projectMemberService.getProjectMembers(1L)).thenReturn(List.of(
                buildMember(10L, "alice@example.com", ProjectRole.OWNER),
                buildMember(20L, "bob@example.com", ProjectRole.EDITOR)
        ));

        mockMvc.perform(get("/api/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(10))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[1].userId").value(20))
                .andExpect(jsonPath("$[1].role").value("EDITOR"));
    }

    @Test
    void getProjectMembers_empty_returns200EmptyArray() throws Exception {
        when(projectMemberService.getProjectMembers(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/projects/1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void inviteMember_validRequest_returns201() throws Exception {
        InviteMemberRequest request = new InviteMemberRequest("new@example.com", ProjectRole.EDITOR);
        when(projectMemberService.inviteMember(eq(1L), any(InviteMemberRequest.class)))
                .thenReturn(buildMember(30L, "new@example.com", ProjectRole.EDITOR));

        mockMvc.perform(post("/api/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(30))
                .andExpect(jsonPath("$.role").value("EDITOR"));
    }

    @Test
    void inviteMember_invalidEmail_returns400() throws Exception {
        InviteMemberRequest request = new InviteMemberRequest("not-an-email", ProjectRole.VIEWER);

        mockMvc.perform(post("/api/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void inviteMember_selfInvite_returns500() throws Exception {
        InviteMemberRequest request = new InviteMemberRequest("owner@example.com", ProjectRole.EDITOR);
        when(projectMemberService.inviteMember(eq(1L), any()))
                .thenThrow(new RuntimeException("Cannot invite yourself"));

        mockMvc.perform(post("/api/projects/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void updateMemberRole_validRequest_returns200() throws Exception {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest(ProjectRole.VIEWER);
        when(projectMemberService.updateMemberRole(eq(1L), eq(20L), any(UpdateMemberRoleRequest.class)))
                .thenReturn(buildMember(20L, "bob@example.com", ProjectRole.VIEWER));

        mockMvc.perform(patch("/api/projects/1/members/20")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(20))
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void removeMember_existingMember_returns204() throws Exception {
        doNothing().when(projectMemberService).removeProjectMember(1L, 20L);

        mockMvc.perform(delete("/api/projects/1/members/20"))
                .andExpect(status().isNoContent());

        verify(projectMemberService).removeProjectMember(1L, 20L);
    }

    @Test
    void removeMember_notFound_returns500() throws Exception {
        doThrow(new RuntimeException("Member not found in project"))
                .when(projectMemberService).removeProjectMember(1L, 99L);

        mockMvc.perform(delete("/api/projects/1/members/99"))
                .andExpect(status().is5xxServerError());
    }
}
