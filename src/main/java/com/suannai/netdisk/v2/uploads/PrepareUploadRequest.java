package com.suannai.netdisk.v2.uploads;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class PrepareUploadRequest {
    @NotNull
    private Long parentId;
    @Valid
    private List<PrepareUploadDirectoryRequest> directories = new ArrayList<>();
    @Valid
    private List<PrepareUploadFileRequest> files = new ArrayList<>();

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public List<PrepareUploadDirectoryRequest> getDirectories() {
        return directories;
    }

    public void setDirectories(List<PrepareUploadDirectoryRequest> directories) {
        this.directories = directories;
    }

    public List<PrepareUploadFileRequest> getFiles() {
        return files;
    }

    public void setFiles(List<PrepareUploadFileRequest> files) {
        this.files = files;
    }
}
