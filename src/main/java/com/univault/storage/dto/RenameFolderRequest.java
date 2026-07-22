package com.univault.storage.dto;

import jakarta.validation.constraints.NotBlank;

public class RenameFolderRequest {

    @NotBlank(message = "Folder name is required")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}