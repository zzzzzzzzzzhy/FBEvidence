package com.evidence.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
public class HashUtil {

    public static String calculateHash(byte[] data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("不支持的哈希算法: {}", algorithm, e);
            throw new RuntimeException("不支持的哈希算法: " + algorithm, e);
        }
    }

    public static String sha256(String data) {
        return calculateHash(data.getBytes(StandardCharsets.UTF_8), "SHA-256");
    }

    public static String sha256(byte[] data) {
        return calculateHash(data, "SHA-256");
    }

    public static String md5(String data) {
        return calculateHash(data.getBytes(StandardCharsets.UTF_8), "MD5");
    }

    public static String md5(byte[] data) {
        return calculateHash(data, "MD5");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public static boolean verifyHash(byte[] data, String hash, String algorithm) {
        String calculatedHash = calculateHash(data, algorithm);
        return calculatedHash.equalsIgnoreCase(hash);
    }
}