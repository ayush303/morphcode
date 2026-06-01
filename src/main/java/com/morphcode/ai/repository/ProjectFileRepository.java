package com.morphcode.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.morphcode.ai.entity.ProjectFile;

import java.util.List;
import java.util.Optional;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {

    Optional<ProjectFile> findByProjectIdAndPath(Long projectId, String cleanPath);

    List<ProjectFile> findByProjectId(Long projectId);
}
