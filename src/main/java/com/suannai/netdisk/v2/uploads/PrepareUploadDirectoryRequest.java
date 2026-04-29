package com.suannai.netdisk.v2.uploads;

public class PrepareUploadDirectoryRequest {
    private String clientId;
    private String path;

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
}
