package com.suannai.netdisk.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class FileHashUtils {

    private FileHashUtils() {
    }

    public static FileHashes digest(Path path) throws IOException {
        MessageDigest md5 = newDigest("MD5");
        MessageDigest sha256 = newDigest("SHA-256");
        try (InputStream inputStream = Files.newInputStream(path);
             DigestInputStream md5Stream = new DigestInputStream(inputStream, md5);
             DigestInputStream shaStream = new DigestInputStream(md5Stream, sha256)) {
            shaStream.transferTo(OutputStream.nullOutputStream());
        }
        return new FileHashes(hex(md5.digest()), hex(sha256.digest()));
    }

    public static String md5Hex(InputStream inputStream) throws IOException {
        MessageDigest md5 = newDigest("MD5");
        try (InputStream stream = inputStream) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                md5.update(buffer, 0, length);
            }
        }
        return hex(md5.digest());
    }

    private static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Hash algorithm is unavailable: " + algorithm, exception);
        }
    }

    private static String hex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }

    public record FileHashes(String md5, String sha256) {
    }

}
