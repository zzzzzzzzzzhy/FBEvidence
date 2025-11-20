package com.evidence.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis分布式锁工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final RedisTemplate<String, String> stringRedisTemplate;

    /**
     * 获取分布式锁
     * @param key 锁的key
     * @param value 锁的值
     * @param expireTime 过期时间
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String key, String value, long expireTime, TimeUnit timeUnit) {
        try {
            Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, value, expireTime, timeUnit);
            log.debug("尝试获取分布式锁: key={}, value={}, result={}", key, value, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("获取分布式锁异常: key={}", key, e);
            return false;
        }
    }

    /**
     * 释放分布式锁
     * @param key 锁的key
     * @param value 锁的值
     * @return 是否释放成功
     */
    public boolean releaseLock(String key, String value) {
        try {
            String luaScript = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "  return redis.call('del', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end";
            
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);
            Long result = stringRedisTemplate.execute(redisScript, Collections.singletonList(key), value);
            log.debug("释放分布式锁: key={}, value={}, result={}", key, value, result);
            return Long.valueOf(1).equals(result);
        } catch (Exception e) {
            log.error("释放分布式锁异常: key={}", key, e);
            return false;
        }
    }

    /**
     * 生成锁的值（UUID）
     */
    public String generateLockValue() {
        return UUID.randomUUID().toString();
    }

    /**
     * 文件上传锁
     */
    public String getUploadLockKey(Long userId, String fileHash) {
        return "upload_lock:" + userId + ":" + fileHash;
    }

    /**
     * 用户注册锁
     */
    public String getRegisterLockKey(String username) {
        return "register_lock:" + username;
    }

    /**
     * DID生成锁
     */
    public String getDIDGenerateLockKey(String username, String email) {
        return "did_generate_lock:" + username + ":" + (email != null ? email : "");
    }

    /**
     * 邮箱注册锁
     */
    public String getEmailRegisterLockKey(String email) {
        return "register_lock:email:" + email;
    }

    /**
     * 使用分布式锁执行业务逻辑
     */
    public <T> T executeWithLock(String lockKey, int expireSeconds, LockCallback<T> callback) {
        String lockValue = generateLockValue();
        boolean locked = false;
        
        try {
            // 尝试获取锁
            locked = tryLock(lockKey, lockValue, expireSeconds, TimeUnit.SECONDS);
            if (!locked) {
                throw new RuntimeException("获取锁失败，请稍后重试");
            }
            
            // 执行业务逻辑
            return callback.execute();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException("执行业务逻辑失败", e);
            }
        } finally {
            // 释放锁
            if (locked) {
                releaseLock(lockKey, lockValue);
            }
        }
    }

    /**
     * 锁回调接口
     */
    @FunctionalInterface
    public interface LockCallback<T> {
        T execute() throws Exception;
    }
}