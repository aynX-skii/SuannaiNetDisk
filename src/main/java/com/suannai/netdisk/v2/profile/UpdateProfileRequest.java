package com.suannai.netdisk.v2.profile;

import javax.validation.constraints.NotBlank;

public class UpdateProfileRequest {
    @NotBlank
    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
