package com.morphcode.ai.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.morphcode.ai.dto.project.FileContentResponse;
import com.morphcode.ai.dto.project.FileNode;
import com.morphcode.ai.service.FileService;

@Service
public class FileServiceImpl implements FileService {

    @Override
    public List<FileNode> getFileTree(Long projectId, Long userId) {
        return List.of();
    }

    @Override
    public FileContentResponse getFileContent(Long projectId, String path, Long userId) {
        return null;
    }
}