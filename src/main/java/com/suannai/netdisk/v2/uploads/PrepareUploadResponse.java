package com.suannai.netdisk.v2.uploads;

import java.util.ArrayList;
import java.util.List;

public class PrepareUploadResponse {
    private List<CreatedDirectoryPayload> createdDirectories = new ArrayList<>();
    private List<InstantFilePayload> instantFiles = new ArrayList<>();
    private List<UploadSessionView> uploadSessions = new ArrayList<>();
    private List<ConflictPayload> conflicts = new ArrayList<>();

    public List<CreatedDirectoryPayload> getCreatedDirectories() {
        return createdDirectories;
    }

    public void setCreatedDirectories(List<CreatedDirectoryPayload> createdDirectories) {
        this.createdDirectories = createdDirectories;
    }

    public List<InstantFilePayload> getInstantFiles() {
        return instantFiles;
    }

    public void setInstantFiles(List<InstantFilePayload> instantFiles) {
        this.instantFiles = instantFiles;
    }

    public List<UploadSessionView> getUploadSessions() {
        return uploadSessions;
    }

    public void setUploadSessions(List<UploadSessionView> uploadSessions) {
        this.uploadSessions = uploadSessions;
    }

    public List<ConflictPayload> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<ConflictPayload> conflicts) {
        this.conflicts = conflicts;
    }

    public static class CreatedDirectoryPayload {
        private String path;
        private Integer directoryId;

        public CreatedDirectoryPayload() {
        }

        public CreatedDirectoryPayload(String path, Integer directoryId) {
            this.path = path;
            this.directoryId = directoryId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Integer getDirectoryId() {
            return directoryId;
        }

        public void setDirectoryId(Integer directoryId) {
            this.directoryId = directoryId;
        }
    }

    public static class InstantFilePayload {
        private String clientId;
        private Integer fileId;
        private String message;

        public InstantFilePayload() {
        }

        public InstantFilePayload(String clientId, Integer fileId, String message) {
            this.clientId = clientId;
            this.fileId = fileId;
            this.message = message;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public Integer getFileId() {
            return fileId;
        }

        public void setFileId(Integer fileId) {
            this.fileId = fileId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class ConflictPayload {
        private String clientId;
        private String path;
        private String reason;

        public ConflictPayload() {
        }

        public ConflictPayload(String clientId, String path, String reason) {
            this.clientId = clientId;
            this.path = path;
            this.reason = reason;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
