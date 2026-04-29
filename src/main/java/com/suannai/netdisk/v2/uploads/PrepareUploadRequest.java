package com.suannai.netdisk.v2.uploads;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class PrepareUploadRequest {
    @NotNull
    private Integer parentId;
    @Valid
    private List<PrepareUploadDirectoryRequest> directories = new ArrayList<>();
    @Valid
    private List<PrepareUploadFileRequest> files = new ArrayList<>();

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
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
