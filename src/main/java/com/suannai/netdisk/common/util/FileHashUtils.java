package com.suannai.netdisk.common.util;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;

public final class FileHashUtils {

    private FileHashUtils() {
    }

    public static String md5Hex(InputStream inputStream) throws IOException {
        try (InputStream stream = inputStream) {
            return DigestUtils.md5Hex(stream);
        }
    }
}
