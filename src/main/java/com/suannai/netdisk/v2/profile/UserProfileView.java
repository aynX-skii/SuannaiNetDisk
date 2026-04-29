package com.suannai.netdisk.v2.profile;

public class UserProfileView {
    private Long id;
    private String username;
    private String nickname;
    private boolean status;
    private Long imgServiceId;
    private String avatarUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public Long getImgServiceId() {
        return imgServiceId;
    }

    public void setImgServiceId(Long imgServiceId) {
        this.imgServiceId = imgServiceId;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
