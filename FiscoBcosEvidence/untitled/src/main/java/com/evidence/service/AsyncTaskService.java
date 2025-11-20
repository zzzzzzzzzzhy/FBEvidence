package com.evidence.service;

import com.evidence.util.RedisQueueUtil;

/**
 * 异步任务处理服务接口
 */
public interface AsyncTaskService {
    
    /**
     * 处理区块链上链任务
     */
    void processBlockchainTask(RedisQueueUtil.BlockchainTask task);
    
    /**
     * 处理文件处理任务
     */
    void processFileProcessTask(RedisQueueUtil.FileProcessTask task);
    
    /**
     * 处理统计更新任务
     */
    void processStatsUpdateTask(RedisQueueUtil.StatsUpdateTask task);
    
    /**
     * 处理数据同步任务
     */
    void processDataSyncTask(RedisQueueUtil.DataSyncTask task);
    
    /**
     * 启动任务消费者
     */
    void startTaskConsumers();
    
    /**
     * 停止任务消费者
     */
    void stopTaskConsumers();
}