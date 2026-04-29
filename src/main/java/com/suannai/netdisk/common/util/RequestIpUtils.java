package com.suannai.netdisk.common.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestIpUtils {
    private static final String[] FORWARDED_HEADERS = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "X-Real-IP"
    };

    private RequestIpUtils() {
    }

    public static String clientIp(HttpServletRequest request) {
        for (String header : FORWARDED_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
