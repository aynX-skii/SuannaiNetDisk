package com.suannai.netdisk.common.util;

import cn.dev33.satoken.stp.StpUtil;

public final class SessionUserHelper {
    public static final String SESSION_USERNAME_KEY = "username";

    private SessionUserHelper() {
    }

    public static SessionUser requireUser() {
        StpUtil.checkLogin();
        Long id = StpUtil.getLoginIdAsLong();
        String username = (String) StpUtil.getSession().get(SESSION_USERNAME_KEY);
        return new SessionUser(id, username == null ? String.valueOf(id) : username);
    }

    public static void signIn(Long id, String username) {
        StpUtil.login(id);
        StpUtil.getSession().set(SESSION_USERNAME_KEY, username);
    }
}
