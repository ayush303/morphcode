package com.morphcode.ai.entity;

import java.time.Instant;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProjectFile {
    Long id;
    String path;
    Project project;
    String miniObjectkey;
    Instant createdAt;
    Instant updatedAt;
    User createdBy;
    User updatedBy;
}
