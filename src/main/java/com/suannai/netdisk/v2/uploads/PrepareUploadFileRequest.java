package com.suannai.netdisk.v2.uploads;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class PrepareUploadFileRequest {
    @NotBlank
    private String clientId;
    @NotBlank
    private String name;
    @NotNull
    private Long size;
    private String relativePath;
    private String md5;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }
}
