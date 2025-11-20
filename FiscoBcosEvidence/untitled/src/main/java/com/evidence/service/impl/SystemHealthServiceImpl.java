package com.evidence.service.impl;

import com.evidence.service.BlockchainService;
import com.evidence.service.SystemHealthService;
import com.evidence.util.FileUtil;
import com.evidence.util.RedisCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统健康监控服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemHealthServiceImpl implements SystemHealthService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final BlockchainService blockchainService;
    private final FileUtil fileUtil;
    private final RedisCacheUtil redisCacheUtil;

    @Override
    public Map<String, Object> getSystemHealth() {
        // 先尝试从缓存获取
        Map<String, Object> cachedHealth = redisCacheUtil.getSystemHealth();
        if (cachedHealth != null) {
            log.debug("从缓存获取系统健康状态");
            return cachedHealth;
        }

        // 缓存中没有，重新检查
        Map<String, Object> health = new HashMap<>();
        
        boolean redisHealth = checkRedisHealth();
        boolean dbHealth = checkDatabaseHealth();
        boolean blockchainHealth = checkBlockchainHealth();
        boolean minioHealth = checkMinIOHealth();
        
        health.put("redis", createHealthStatus(redisHealth, "Redis缓存服务"));
        health.put("database", createHealthStatus(dbHealth, "MySQL数据库"));
        health.put("blockchain", createHealthStatus(blockchainHealth, "FISCO BCOS区块链"));
        health.put("minio", createHealthStatus(minioHealth, "MinIO对象存储"));
        
        boolean overallHealth = redisHealth && dbHealth && blockchainHealth && minioHealth;
        health.put("status", overallHealth ? "UP" : "DOWN");
        health.put("timestamp", System.currentTimeMillis());
        
        // 缓存健康状态
        redisCacheUtil.cacheSystemHealth(health);
        
        return health;
    }

    @Override
    public boolean checkRedisHealth() {
        try {
            redisTemplate.opsForValue().set("health:check", "ping");
            String result = (String) redisTemplate.opsForValue().get("health:check");
            redisTemplate.delete("health:check");
            return "ping".equals(result);
        } catch (Exception e) {
            log.error("Redis健康检查失败", e);
            return false;
        }
    }

    @Override
    public boolean checkDatabaseHealth() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.error("数据库健康检查失败", e);
            return false;
        }
    }

    @Override
    public boolean checkBlockchainHealth() {
        try {
            Long blockNumber = blockchainService.getBlockNumber();
            return blockNumber != null && blockNumber > 0;
        } catch (Exception e) {
            log.error("区块链健康检查失败", e);
            return false;
        }
    }

    @Override
    public boolean checkMinIOHealth() {
        try {
            fileUtil.testMinIOConnection();
            return true;
        } catch (Exception e) {
            log.error("MinIO健康检查失败", e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getSystemStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 获取总存证数量
            String totalEvidenceSql = "SELECT COUNT(*) FROM file_evidence";
            Integer totalEvidence = jdbcTemplate.queryForObject(totalEvidenceSql, Integer.class);
            stats.put("totalEvidence", totalEvidence != null ? totalEvidence : 0);

            // 获取存储总量（字节）
            String totalStorageSql = "SELECT COALESCE(SUM(file_size), 0) FROM file_evidence WHERE file_size IS NOT NULL";
            Long totalStorage = jdbcTemplate.queryForObject(totalStorageSql, Long.class);
            stats.put("totalStorage", totalStorage != null ? totalStorage : 0L);

            // 获取今日存证数量
            String today = LocalDate.now().toString();
            String todayEvidenceSql = "SELECT COUNT(*) FROM file_evidence WHERE DATE(created_at) = ?";
            Integer todayEvidence = jdbcTemplate.queryForObject(todayEvidenceSql, Integer.class, today);
            stats.put("todayEvidence", todayEvidence != null ? todayEvidence : 0);

            // 获取用户总数（保留后端功能）
            String totalUsersSql = "SELECT COUNT(*) FROM users";
            Integer totalUsers = jdbcTemplate.queryForObject(totalUsersSql, Integer.class);
            stats.put("totalUsers", totalUsers != null ? totalUsers : 0);

            // 获取总交易数（保留后端功能 - 通过区块链查询）
            try {
                Long currentHeight = blockchainService.getBlockNumber();
                // 估算总交易数：假设平均每个区块有3个交易
                long estimatedTransactions = currentHeight != null ? currentHeight * 3 : 0;
                stats.put("totalTransactions", estimatedTransactions);
            } catch (Exception e) {
                log.warn("获取区块链交易统计失败", e);
                stats.put("totalTransactions", 0);
            }

            // 获取今日交易数（保留后端功能）
            String todayTransactionsSql = "SELECT COUNT(*) FROM file_evidence WHERE DATE(created_at) = ? AND transaction_hash IS NOT NULL";
            Integer todayTransactions = jdbcTemplate.queryForObject(todayTransactionsSql, Integer.class, today);
            stats.put("todayTransactions", todayTransactions != null ? todayTransactions : 0);

            log.info("系统统计信息: 总存证={}, 存储总量={}字节, 今日存证={}",
                    stats.get("totalEvidence"), stats.get("totalStorage"), stats.get("todayEvidence"));

        } catch (Exception e) {
            log.error("获取系统统计信息失败", e);
            // 失败时返回默认值
            stats.put("totalEvidence", 0);
            stats.put("totalStorage", 0L);
            stats.put("todayEvidence", 0);
            stats.put("totalUsers", 0);
            stats.put("totalTransactions", 0);
            stats.put("todayTransactions", 0);
        }

        return stats;
    }

    /**
     * 创建健康状态对象
     */
    private Map<String, Object> createHealthStatus(boolean healthy, String description) {
        Map<String, Object> status = new HashMap<>();
        status.put("status", healthy ? "UP" : "DOWN");
        status.put("description", description);
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    /**
     * 获取在线用户数（简化实现）
     */
    private long getOnlineUserCount() {
        try {
            // 这里可以通过统计Redis中以"user:online:"开头的key数量来实现
            // 为了简化，这里返回一个估算值
            return 0; // 实际实现需要查询Redis
        } catch (Exception e) {
            log.error("获取在线用户数失败", e);
            return 0;
        }
    }

    /**
     * 获取系统运行时间
     */
    private long getSystemUptime() {
        return System.currentTimeMillis() - startTime;
    }

    // 系统启动时间（简化实现）
    private static final long startTime = System.currentTimeMillis();
}