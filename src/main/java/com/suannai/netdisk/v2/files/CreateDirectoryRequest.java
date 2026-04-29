package com.suannai.netdisk.v2.files;

import jakarta.validation.constraints.NotBlank;

public class CreateDirectoryRequest {
    private Long parentId;
    @NotBlank
    private String name;

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
