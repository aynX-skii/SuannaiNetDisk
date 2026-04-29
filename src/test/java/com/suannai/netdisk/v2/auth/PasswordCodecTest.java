package com.suannai.netdisk.v2.auth;

import com.suannai.netdisk.common.util.PasswordCodec;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordCodecTest {

    private final PasswordCodec passwordCodec = new PasswordCodec();

    @Test
    void matchesLegacyMd5Password() {
        String legacyHash = DigestUtils.md5Hex("secret123");
        assertTrue(passwordCodec.matches("secret123", legacyHash, "MD5"));
    }

    @Test
    void encodesAndMatchesBcryptPassword() {
        String encoded = passwordCodec.encode("secret123");
        assertNotEquals("secret123", encoded);
        assertTrue(passwordCodec.matches("secret123", encoded, "BCRYPT"));
    }
}
