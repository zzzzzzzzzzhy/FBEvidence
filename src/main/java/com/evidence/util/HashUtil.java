package com.evidence.util;

import cn.hutool.core.util.HexUtil;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SM3Digest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
public class HashUtil {

    public static String sm3(String data) {
        return sm3(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String sm3(byte[] data) {
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return HexUtil.encodeHexStr(out);
    }

    public static String calculateHash(byte[] data, String algorithm) {
        if ("SM3".equalsIgnoreCase(algorithm)) {
            return sm3(data);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("不支持的哈希算法: {}", algorithm, e);
            throw new RuntimeException("不支持的哈希算法: " + algorithm, e);
        }
    }

    public static String md5(String data) {
        return calculateHash(data.getBytes(StandardCharsets.UTF_8), "MD5");
    }

    public static String md5(byte[] data) {
        return calculateHash(data, "MD5");
    }

    public static boolean verifyHash(byte[] data, String hash, String algorithm) {
        return calculateHash(data, algorithm).equalsIgnoreCase(hash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
