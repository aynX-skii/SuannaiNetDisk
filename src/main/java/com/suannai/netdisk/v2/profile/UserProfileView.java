package com.suannai.netdisk.v2.profile;

public class UserProfileView {
    private Integer id;
    private String username;
    private String nickname;
    private boolean status;
    private Integer imgServiceId;
    private String avatarUrl;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public Integer getImgServiceId() {
        return imgServiceId;
    }

    public void setImgServiceId(Integer imgServiceId) {
        this.imgServiceId = imgServiceId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
