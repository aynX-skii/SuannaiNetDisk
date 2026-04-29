package com.suannai.netdisk.common.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordCodec {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword, String algorithm) {
        if ("BCRYPT".equalsIgnoreCase(algorithm)) {
            return encoder.matches(rawPassword, encodedPassword);
        }
        return DigestUtils.md5Hex(rawPassword).equalsIgnoreCase(encodedPassword);
    }
}
