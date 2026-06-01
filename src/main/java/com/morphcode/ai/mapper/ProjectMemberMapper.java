package com.morphcode.ai.mapper;

import com.morphcode.ai.dto.member.MemberResponse;
import com.morphcode.ai.entity.ProjectMember;
import com.morphcode.ai.entity.User;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectMemberMapper {

    @Mapping(target = "userId", source = "id")
    @Mapping(target = "role", constant = "OWNER")
    @Mapping(target = "email", source = "username")
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "invitedAt", ignore = true)
    MemberResponse toProjectMemberResponseFromOwner(User owner);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "name", source = "user.name")
    @Mapping(target = "email", source = "user.username")
    @Mapping(target = "avatarUrl", ignore = true)
    @Mapping(target = "role", source = "projectRole")
    MemberResponse toProjectMemberResponseFromMember(ProjectMember projectMember);
}
