package com.morphcode.ai.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.morphcode.ai.dto.project.FileNode;
import com.morphcode.ai.entity.ProjectFile;

@Mapper(componentModel = "spring")
public interface ProjectFileMapper {
    List<FileNode> toListOfFileNode(List<ProjectFile> projectFileList);
}
