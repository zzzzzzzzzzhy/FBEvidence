package com.evidence.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    @Test
    void testMiMa(){
        System.out.println(new BCryptPasswordEncoder(10).encode("123123"));
    }

    @Test
    void testSha256String() {
        String input = "Hello World";
        String expected = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";

        String result = HashUtil.sha256(input);

        assertEquals(expected, result);
    }

    @Test
    void testSha256Bytes() {
        byte[] input = "Hello World".getBytes();
        String expected = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e";

        String result = HashUtil.sha256(input);

        assertEquals(expected, result);
    }

    @Test
    void testMd5String() {
        String input = "Hello World";
        String expected = "b10a8db164e0754105b7a99be72e3fe5";

        String result = HashUtil.md5(input);

        assertEquals(expected, result);
    }

    @Test
    void testCalculateHash() {
        String input = "Hello World";
        byte[] data = input.getBytes();

        String sha256Result = HashUtil.calculateHash(data, "SHA-256");
        String md5Result = HashUtil.calculateHash(data, "MD5");

        assertNotNull(sha256Result);
        assertNotNull(md5Result);
        assertNotEquals(sha256Result, md5Result);

        // SHA-256 结果应该是64个字符（32字节的十六进制表示）
        assertEquals(64, sha256Result.length());

        // MD5 结果应该是32个字符（16字节的十六进制表示）
        assertEquals(32, md5Result.length());
    }

    @Test
    void testCalculateHashUnsupportedAlgorithm() {
        byte[] data = "Hello World".getBytes();

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            HashUtil.calculateHash(data, "UNSUPPORTED");
        });

        assertTrue(exception.getMessage().contains("不支持的哈希算法"));
    }

    @Test
    void testVerifyHash() {
        String input = "Hello World";
        byte[] data = input.getBytes();
        String hash = HashUtil.sha256(input);

        // 验证正确的哈希
        assertTrue(HashUtil.verifyHash(data, hash, "SHA-256"));

        // 验证错误的哈希
        assertFalse(HashUtil.verifyHash(data, "wrong_hash", "SHA-256"));

        // 验证不同的数据
        byte[] wrongData = "Wrong Data".getBytes();
        assertFalse(HashUtil.verifyHash(wrongData, hash, "SHA-256"));
    }
}
