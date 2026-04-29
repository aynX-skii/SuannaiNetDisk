package com.suannai.netdisk.common.util;

import com.suannai.netdisk.common.exception.ApiException;
import com.suannai.netdisk.model.User;

import javax.servlet.http.HttpSession;

public final class SessionUserHelper {

    private SessionUserHelper() {
    }

    public static User requireUser(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            throw new ApiException("UNAUTHORIZED", "请先登录");
        }
        return user;
    }
}
