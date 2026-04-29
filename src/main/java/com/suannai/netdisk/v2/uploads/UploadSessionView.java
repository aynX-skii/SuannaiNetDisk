package com.suannai.netdisk.v2.uploads;

import java.util.ArrayList;
import java.util.List;

public class UploadSessionView {
    private String uploadId;
    private String clientId;
    private Long targetParentId;
    private Integer chunkSize;
    private Integer totalParts;
    private List<Integer> uploadedParts = new ArrayList<>();
    private String expiresAt;

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Long getTargetParentId() {
        return targetParentId;
    }

    public void setTargetParentId(Long targetParentId) {
        this.targetParentId = targetParentId;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getTotalParts() {
        return totalParts;
    }

    public void setTotalParts(Integer totalParts) {
        this.totalParts = totalParts;
    }

    public List<Integer> getUploadedParts() {
        return uploadedParts;
    }

    public void setUploadedParts(List<Integer> uploadedParts) {
        this.uploadedParts = uploadedParts;
    }

    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }
}
