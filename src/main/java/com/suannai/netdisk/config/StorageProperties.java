package com.suannai.netdisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "netdisk.storage")
public class StorageProperties {
    private String uploadPath;
    private String tempDirName = ".tmp";
    private int uploadSessionTtlHours = 24;

    public String getUploadPath() {
        return uploadPath;
    }

    public void setUploadPath(String uploadPath) {
        this.uploadPath = uploadPath;
    }

    public String getTempDirName() {
        return tempDirName;
    }

    public void setTempDirName(String tempDirName) {
        this.tempDirName = tempDirName;
    }

    public int getUploadSessionTtlHours() {
        return uploadSessionTtlHours;
    }

    public void setUploadSessionTtlHours(int uploadSessionTtlHours) {
        this.uploadSessionTtlHours = uploadSessionTtlHours;
    }
}
