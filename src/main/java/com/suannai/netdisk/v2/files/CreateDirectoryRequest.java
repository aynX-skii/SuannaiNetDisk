package com.suannai.netdisk.v2.files;

import javax.validation.constraints.NotBlank;

public class CreateDirectoryRequest {
    private Integer parentId;
    @NotBlank
    private String name;

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
