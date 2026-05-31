package com.evidence.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    private final RedisTemplate<String, String> stringRedisTemplate;

    private final ObjectMapper objectMapper;

    // ================================ 基础操作 ================================

    /**
     * 设置缓存
     */
    public void set(String key, Object value, long timeout, TimeUnit timeUnit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
            log.debug("设置缓存成功: key={}", key);
        } catch (Exception e) {
            log.error("设置缓存失败: key={}", key, e);
        }
    }

    /**
     * 获取缓存
     */
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("获取缓存失败: key={}", key, e);
            return null;
        }
    }

    /**
     * 删除缓存
     */
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            log.debug("删除缓存: key={}, result={}", key, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("删除缓存失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 批量删除缓存
     */
    public long deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long result = redisTemplate.delete(keys);
                log.debug("批量删除缓存: pattern={}, count={}", pattern, result);
                return result != null ? result : 0;
            }
            return 0;
        } catch (Exception e) {
            log.error("批量删除缓存失败: pattern={}", pattern, e);
            return 0;
        }
    }

    /**
     * 判断key是否存在
     */
    public boolean exists(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("判断key存在失败: key={}", key, e);
            return false;
        }
    }

    /**
     * 设置key的过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit timeUnit) {
        try {
            Boolean result = redisTemplate.expire(key, timeout, timeUnit);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("设置过期时间失败: key={}", key, e);
            return false;
        }
    }

    // ================================ 业务相关缓存 ================================

    /**
     * 缓存用户信息
     */
    public void cacheUserInfo(Long userId, Object userInfo) {
        String key = getUserInfoKey(userId);
        set(key, userInfo, 30, TimeUnit.MINUTES);
    }

    /**
     * 获取缓存的用户信息
     */
    public Object getCachedUserInfo(Long userId) {
        String key = getUserInfoKey(userId);
        return get(key);
    }

    /**
     * 删除用户信息缓存
     */
    public boolean deleteCachedUserInfo(Long userId) {
        String key = getUserInfoKey(userId);
        return delete(key);
    }

    /**
     * 缓存文件哈希存在性
     */
    public void cacheFileHashExists(String fileHash, boolean exists) {
        String key = getFileHashExistsKey(fileHash);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(exists), 24, TimeUnit.HOURS);
    }

    /**
     * 检查文件哈希是否存在（从缓存）
     */
    public Boolean getFileHashExists(String fileHash) {
        String key = getFileHashExistsKey(fileHash);
        String value = stringRedisTemplate.opsForValue().get(key);
        return value != null ? Boolean.valueOf(value) : null;
    }

    /**
     * 缓存用户统计信息
     */
    public void cacheUserStats(Long userId, Map<String, Object> stats) {
        String key = getUserStatsKey(userId);
        set(key, stats, 5, TimeUnit.MINUTES);
    }

    /**
     * 获取缓存的用户统计信息
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedUserStats(Long userId) {
        String key = getUserStatsKey(userId);
        Object stats = get(key);
        return stats instanceof Map ? (Map<String, Object>) stats : null;
    }

    /**
     * 删除用户统计缓存
     */
    public boolean deleteCachedUserStats(Long userId) {
        String key = getUserStatsKey(userId);
        return delete(key);
    }

    // ================================ JWT Token黑名单 ================================

    /**
     * 将token加入黑名单
     */
    public void addTokenToBlacklist(String tokenHash, long expireSeconds) {
        String key = getTokenBlacklistKey(tokenHash);
        stringRedisTemplate.opsForValue().set(key, "1", expireSeconds, TimeUnit.SECONDS);
        log.info("Token加入黑名单: {}", tokenHash);
    }

    /**
     * 检查token是否在黑名单中
     */
    public boolean isTokenBlacklisted(String tokenHash) {
        String key = getTokenBlacklistKey(tokenHash);
        return exists(key);
    }

    // ================================ 登录失败次数限制 ================================

    /**
     * 增加登录失败次数
     */
    public long incrementLoginFailCount(String username) {
        String key = getLoginFailKey(username);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 首次失败，设置过期时间30分钟
            stringRedisTemplate.expire(key, 30, TimeUnit.MINUTES);
        }
        log.debug("用户{}登录失败次数: {}", username, count);
        return count != null ? count : 0;
    }

    /**
     * 获取登录失败次数
     */
    public long getLoginFailCount(String username) {
        String key = getLoginFailKey(username);
        String count = stringRedisTemplate.opsForValue().get(key);
        return count != null ? Long.parseLong(count) : 0;
    }

    /**
     * 清除登录失败次数
     */
    public boolean clearLoginFailCount(String username) {
        String key = getLoginFailKey(username);
        return delete(key);
    }

    // ================================ 用户在线状态 ================================

    /**
     * 更新用户在线状态
     */
    public void updateUserOnline(Long userId) {
        String key = getUserOnlineKey(userId);
        stringRedisTemplate.opsForValue().set(key, "1", 30, TimeUnit.MINUTES);
        
        // 同时更新活跃用户统计
        updateUserActivity(userId);
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(Long userId) {
        String key = getUserOnlineKey(userId);
        return exists(key);
    }

    /**
     * 更新用户活跃度统计
     */
    private void updateUserActivity(Long userId) {
        String today = LocalDate.now().toString();
        String thisMonth = YearMonth.now().toString();
        
        stringRedisTemplate.opsForHyperLogLog().add(getDAUKey(today), userId.toString());
        stringRedisTemplate.opsForHyperLogLog().add(getMAUKey(thisMonth), userId.toString());
    }

    /**
     * 获取日活跃用户数
     */
    public long getDAU(String date) {
        String key = getDAUKey(date);
        Long count = stringRedisTemplate.opsForHyperLogLog().size(key);
        return count != null ? count : 0;
    }

    /**
     * 获取月活跃用户数
     */
    public long getMAU(String month) {
        String key = getMAUKey(month);
        Long count = stringRedisTemplate.opsForHyperLogLog().size(key);
        return count != null ? count : 0;
    }

    // ================================ 系统健康状态 ================================

    /**
     * 缓存系统健康状态
     */
    public void cacheSystemHealth(Map<String, Object> healthStatus) {
        String key = getSystemHealthKey();
        set(key, healthStatus, 1, TimeUnit.MINUTES);
    }

    /**
     * 获取系统健康状态
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSystemHealth() {
        String key = getSystemHealthKey();
        Object health = get(key);
        return health instanceof Map ? (Map<String, Object>) health : null;
    }

    // ================================ Key生成方法 ================================

    private String getUserInfoKey(Long userId) {
        return "user:info:" + userId;
    }

    private String getFileHashExistsKey(String fileHash) {
        return "file:hash:exists:" + fileHash;
    }

    private String getUserStatsKey(Long userId) {
        return "user:stats:" + userId;
    }

    private String getTokenBlacklistKey(String tokenHash) {
        return "token:blacklist:" + tokenHash;
    }

    private String getLoginFailKey(String username) {
        return "login_fail:" + username;
    }

    private String getUserOnlineKey(Long userId) {
        return "user:online:" + userId;
    }

    private String getDAUKey(String date) {
        return "stats:dau:" + date;
    }

    private String getMAUKey(String month) {
        return "stats:mau:" + month;
    }

    private String getSystemHealthKey() {
        return "system:health:status";
    }
}