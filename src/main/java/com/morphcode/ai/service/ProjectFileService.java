package com.morphcode.ai.service;

import com.morphcode.ai.dto.project.FileContentResponse;
import com.morphcode.ai.dto.project.FileTreeResponse;

public interface ProjectFileService {
    FileTreeResponse getFileTree(Long projectId);

    FileContentResponse getFileContent(Long projectId, String path);

    void saveFile(Long projectId, String filePath, String fileContent);
}
