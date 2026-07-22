package com.univault.storage.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class CreateFolderRequest {

    @NotBlank(message = "Folder name is required")
    private String name;

    // null = create at root level (no parent)
    private UUID parentFolderId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(UUID parentFolderId) {
        this.parentFolderId = parentFolderId;
    }
}