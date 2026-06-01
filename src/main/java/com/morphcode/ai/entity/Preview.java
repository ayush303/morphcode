package com.morphcode.ai.entity;

import java.time.Instant;

import com.morphcode.ai.enums.PreviewStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Preview {
    Long id;
    Project project;
    String namespace;
    String previewUrl;
    String podName;
    Instant startedAt;
    Instant terminatedAt;
    Instant createdAt;
    PreviewStatus status;
}
