package com.morphcode.ai.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.morphcode.ai.dto.member.InviteMemberRequest;
import com.morphcode.ai.dto.member.MemberResponse;
import com.morphcode.ai.dto.member.UpdateMemberRoleRequest;
import com.morphcode.ai.service.ProjectMemberService;

@Service
public class ProjectMemberServiceImpl implements ProjectMemberService {
    @Override
    public List<MemberResponse> getProjectMembers(Long projectId, Long userId) {
        return List.of();
    }

    @Override
    public MemberResponse inviteMember(Long projectId, InviteMemberRequest request, Long userId) {
        return null;
    }

    @Override
    public MemberResponse updateMemberRole(Long projectId, Long memberId,
            UpdateMemberRoleRequest request,
            Long userId) {
        return null;
    }

    @Override
    public MemberResponse deleteProjectMember(Long projectId, Long memberId, Long userId) {
        return null;
    }
}
