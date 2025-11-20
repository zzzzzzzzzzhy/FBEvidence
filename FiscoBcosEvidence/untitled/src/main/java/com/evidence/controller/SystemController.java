package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.service.SystemHealthService;
import com.evidence.util.RedisCacheUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统监控控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemHealthService systemHealthService;
    private final RedisCacheUtil redisCacheUtil;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取系统健康状态
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> getSystemHealth() {
        log.info("=== Controller: /api/system/health接口接收到请求 ===");
        try {
            Map<String, Object> health = systemHealthService.getSystemHealth();
            return Result.success(health);
        } catch (Exception e) {
            log.error("获取系统健康状态失败", e);
            return Result.error("获取系统健康状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取系统统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getSystemStats() {
        log.info("=== Controller: /api/system/stats接口接收到请求 ===");
        try {
            Map<String, Object> stats = systemHealthService.getSystemStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取系统统计信息失败", e);
            return Result.error("获取系统统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户活跃度统计
     */
    @GetMapping("/activity")
    public Result<Map<String, Object>> getUserActivity() {
        log.info("=== Controller: /api/system/activity接口接收到请求 ===");
        try {
            Map<String, Object> activity = new HashMap<>();
            
            String today = LocalDate.now().toString();
            String thisMonth = YearMonth.now().toString();
            
            long dau = redisCacheUtil.getDAU(today);
            long mau = redisCacheUtil.getMAU(thisMonth);
            
            activity.put("dau", dau);
            activity.put("mau", mau);
            activity.put("date", today);
            activity.put("month", thisMonth);
            
            return Result.success(activity);
        } catch (Exception e) {
            log.error("获取用户活跃度统计失败", e);
            return Result.error("获取用户活跃度统计失败: " + e.getMessage());
        }
    }

    /**
     * 清理缓存
     */
    @PostMapping("/cache/clear")
    public Result<String> clearCache(@RequestParam(required = false) String pattern) {
        log.info("=== Controller: /api/system/cache/clear接口接收到请求 ===");
        try {
            if (pattern != null && !pattern.trim().isEmpty()) {
                // 清理指定模式的缓存
                long count = redisCacheUtil.deleteByPattern(pattern);
                return Result.success("已清理 " + count + " 个缓存项");
            } else {
                return Result.error("请指定要清理的缓存模式");
            }
        } catch (Exception e) {
            log.error("清理缓存失败", e);
            return Result.error("清理缓存失败: " + e.getMessage());
        }
    }

    /**
     * 测试Redis连接
     */
    @GetMapping("/redis/test")
    public Result<String> testRedis() {
        log.info("=== Controller: /api/system/redis/test接口接收到请求 ===");
        try {
            boolean healthy = systemHealthService.checkRedisHealth();
            if (healthy) {
                return Result.success("Redis连接正常");
            } else {
                return Result.error("Redis连接异常");
            }
        } catch (Exception e) {
            log.error("Redis连接测试失败", e);
            return Result.error("Redis连接测试失败: " + e.getMessage());
        }
    }

    /**
     * 数据库迁移 - 添加did_document字段
     */
    @PostMapping("/migrate/did-document")
    public Result<String> migrateDidDocument() {
        log.info("=== Controller: /api/system/migrate/did-document接口接收到请求 ===");
        try {
            // 检查字段是否已存在
            String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                             "WHERE TABLE_SCHEMA = 'blockchain_evidence' " +
                             "AND TABLE_NAME = 'file_evidence' " +
                             "AND COLUMN_NAME = 'did_document'";

            Integer exists = jdbcTemplate.queryForObject(checkSql, Integer.class);

            if (exists != null && exists > 0) {
                return Result.success("did_document字段已存在，无需添加");
            }

            // 添加字段
            String alterSql = "ALTER TABLE file_evidence ADD COLUMN did_document VARCHAR(128) " +
                             "COMMENT '文件DID标识文档' AFTER chain_message";
            jdbcTemplate.execute(alterSql);

            // 为现有文件生成DID文档
            String updateSql = "UPDATE file_evidence SET did_document = CONCAT('did:evidence:file:', file_hash) " +
                              "WHERE did_document IS NULL AND file_hash IS NOT NULL";
            int updateCount = jdbcTemplate.update(updateSql);

            log.info("did_document字段添加成功，更新了 {} 条记录", updateCount);
            return Result.success("did_document字段添加成功，更新了 " + updateCount + " 条记录");

        } catch (Exception e) {
            log.error("数据库迁移失败", e);
            return Result.error("数据库迁移失败: " + e.getMessage());
        }
    }
}