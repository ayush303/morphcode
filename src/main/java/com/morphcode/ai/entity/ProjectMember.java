package com.morphcode.ai.entity;

import java.time.Instant;

import com.morphcode.ai.enums.ProjectRole;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProjectMember {
    ProjectMemberId id;
    User user;
    Project project;
    ProjectRole role;
    Instant invitedAt;
    Instant acceptedAt;
}
