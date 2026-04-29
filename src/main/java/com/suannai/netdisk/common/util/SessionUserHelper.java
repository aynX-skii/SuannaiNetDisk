package com.suannai.netdisk.common.util;

import com.suannai.netdisk.common.exception.ApiException;

import jakarta.servlet.http.HttpSession;

public final class SessionUserHelper {
    public static final String SESSION_USER_KEY = "user";

    private SessionUserHelper() {
    }

    public static SessionUser requireUser(HttpSession session) {
        SessionUser user = (SessionUser) session.getAttribute(SESSION_USER_KEY);
        if (user == null) {
            throw new ApiException("UNAUTHORIZED", "请先登录");
        }
        return user;
    }

    public static void signIn(HttpSession session, Long id, String username) {
        session.setAttribute(SESSION_USER_KEY, new SessionUser(id, username));
    }
}
