package com.morphcode.ai.service;

import java.util.List;

import com.morphcode.ai.dto.project.FileContentResponse;
import com.morphcode.ai.dto.project.FileNode;

public interface FileService {
    List<FileNode> getFileTree(Long projectId, Long userId);

    FileContentResponse getFileContent(Long projectId, String path, Long userId);
}
