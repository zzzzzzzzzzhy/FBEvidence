package com.evidence.service;

import java.util.Map;

/**
 * 系统健康监控服务接口
 */
public interface SystemHealthService {
    
    /**
     * 获取系统健康状态
     */
    Map<String, Object> getSystemHealth();
    
    /**
     * 检查Redis连接状态
     */
    boolean checkRedisHealth();
    
    /**
     * 检查数据库连接状态
     */
    boolean checkDatabaseHealth();
    
    /**
     * 检查区块链连接状态
     */
    boolean checkBlockchainHealth();
    
    /**
     * 检查MinIO连接状态
     */
    boolean checkMinIOHealth();
    
    /**
     * 获取系统统计信息
     */
    Map<String, Object> getSystemStats();
}