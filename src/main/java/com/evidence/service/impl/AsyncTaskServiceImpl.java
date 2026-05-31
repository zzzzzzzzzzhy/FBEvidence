package com.evidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evidence.common.Constants;
import com.evidence.entity.FileEvidence;
import com.evidence.service.AsyncTaskService;
import com.evidence.service.BlockchainService;
import com.evidence.service.EvidenceService;
import com.evidence.util.RedisCacheUtil;
import com.evidence.util.RedisQueueUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 异步任务处理服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTaskServiceImpl implements AsyncTaskService {

    private final RedisQueueUtil redisQueueUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final BlockchainService blockchainService;
    private final EvidenceService evidenceService;
    
    private volatile boolean running = false;

    @PostConstruct
    public void init() {
        startTaskConsumers();
    }

    @PreDestroy
    public void destroy() {
        stopTaskConsumers();
    }

    @Override
    public void startTaskConsumers() {
        if (!running) {
            running = true;
            log.info("启动异步任务消费者");
            
            // 启动区块链任务消费者
            CompletableFuture.runAsync(this::consumeBlockchainTasks);
            
            // 启动文件处理任务消费者
            CompletableFuture.runAsync(this::consumeFileProcessTasks);
            
            // 启动统计更新任务消费者
            CompletableFuture.runAsync(this::consumeStatsUpdateTasks);
            
            // 启动数据同步任务消费者
            CompletableFuture.runAsync(this::consumeDataSyncTasks);
        }
    }

    @Override
    public void stopTaskConsumers() {
        running = false;
        log.info("停止异步任务消费者");
    }

    /**
     * 消费区块链任务队列
     */
    private void consumeBlockchainTasks() {
        while (running) {
            try {
                RedisQueueUtil.BlockchainTask task = redisQueueUtil.popBlockchainTask();
                if (task != null) {
                    processBlockchainTask(task);
                } else {
                    // 没有任务时短暂休眠
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log.error("消费区块链任务失败", e);
                try {
                    Thread.sleep(5000); // 出错时休眠5秒
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 消费文件处理任务队列
     */
    private void consumeFileProcessTasks() {
        while (running) {
            try {
                RedisQueueUtil.FileProcessTask task = redisQueueUtil.popFileProcessTask();
                if (task != null) {
                    processFileProcessTask(task);
                } else {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log.error("消费文件处理任务失败", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 消费统计更新任务队列
     */
    private void consumeStatsUpdateTasks() {
        while (running) {
            try {
                RedisQueueUtil.StatsUpdateTask task = redisQueueUtil.popStatsUpdateTask();
                if (task != null) {
                    processStatsUpdateTask(task);
                } else {
                    Thread.sleep(2000); // 统计更新不需要太频繁
                }
            } catch (Exception e) {
                log.error("消费统计更新任务失败", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 消费数据同步任务队列
     */
    private void consumeDataSyncTasks() {
        while (running) {
            try {
                RedisQueueUtil.DataSyncTask task = redisQueueUtil.popDataSyncTask();
                if (task != null) {
                    processDataSyncTask(task);
                } else {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                log.error("消费数据同步任务失败", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @Override
    @Async
    public void processBlockchainTask(RedisQueueUtil.BlockchainTask task) {
        log.info("开始处理区块链上链任务: evidenceId={}, fileHash={}", 
                task.getEvidenceId(), task.getFileHash());
        
        try {
            // 调用区块链服务上链
            TransactionReceipt receipt = blockchainService.addEvidence(
                task.getFileHash(),
                task.getFileName(),
                task.getUploader(),
                task.getFileSize(),
                task.getDescription()
            );

            // 更新数据库中的区块链信息
            LambdaUpdateWrapper<FileEvidence> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(FileEvidence::getId, task.getEvidenceId())
                    .set(FileEvidence::getTransactionHash, receipt.getTransactionHash())
                    .set(FileEvidence::getBlockNumber, parseBlockNumber(receipt.getBlockNumber()))
                    .set(FileEvidence::getChainStatus, Constants.CHAIN_STATUS_SUCCESS)
                    .set(FileEvidence::getChainMessage, "上链成功");

            evidenceService.update(updateWrapper);

            // 清除相关缓存
            clearRelatedCache(task);

            log.info("区块链上链任务处理成功: evidenceId={}, txHash={}", 
                    task.getEvidenceId(), receipt.getTransactionHash());

        } catch (Exception e) {
            log.error("区块链上链任务处理失败: evidenceId={}", task.getEvidenceId(), e);
            
            // 增加重试次数
            task.setRetryCount(task.getRetryCount() + 1);
            
            if (task.getRetryCount() < 3) {
                // 重新加入队列重试
                log.info("重新加入队列重试: evidenceId={}, retryCount={}", 
                        task.getEvidenceId(), task.getRetryCount());
                redisQueueUtil.pushBlockchainTask(task);
            } else {
                // 超过重试次数，标记为失败
                LambdaUpdateWrapper<FileEvidence> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(FileEvidence::getId, task.getEvidenceId())
                        .set(FileEvidence::getChainStatus, Constants.CHAIN_STATUS_FAILED)
                        .set(FileEvidence::getChainMessage, "上链失败: " + e.getMessage());

                evidenceService.update(updateWrapper);
                clearRelatedCache(task);
                
                log.error("区块链上链任务最终失败: evidenceId={}", task.getEvidenceId());
            }
        }
    }

    @Override
    @Async
    public void processFileProcessTask(RedisQueueUtil.FileProcessTask task) {
        log.info("开始处理文件处理任务: evidenceId={}, operation={}", 
                task.getEvidenceId(), task.getOperation());
        
        try {
            switch (task.getOperation()) {
                case "HASH_CALCULATE":
                    // 处理哈希计算任务
                    break;
                case "VALIDATE":
                    // 处理文件验证任务
                    break;
                default:
                    log.warn("未知的文件处理操作: {}", task.getOperation());
                    break;
            }
            
            log.info("文件处理任务完成: evidenceId={}", task.getEvidenceId());
            
        } catch (Exception e) {
            log.error("文件处理任务失败: evidenceId={}", task.getEvidenceId(), e);
            
            task.setRetryCount(task.getRetryCount() + 1);
            if (task.getRetryCount() < 3) {
                redisQueueUtil.pushFileProcessTask(task);
            }
        }
    }

    @Override
    @Async
    public void processStatsUpdateTask(RedisQueueUtil.StatsUpdateTask task) {
        log.debug("开始处理统计更新任务: userId={}, operation={}", 
                task.getUserId(), task.getOperation());
        
        try {
            switch (task.getOperation()) {
                case "UPDATE_USER_STATS":
                    // 清除用户统计缓存，触发重新计算
                    if (task.getUserId() != null) {
                        redisCacheUtil.deleteCachedUserStats(task.getUserId());
                        log.debug("已清除用户统计缓存: userId={}", task.getUserId());
                    }
                    break;
                case "UPDATE_SYSTEM_STATS":
                    // 更新系统级统计信息
                    // 可以在这里实现系统级别的统计更新逻辑
                    break;
                default:
                    log.warn("未知的统计更新操作: {}", task.getOperation());
                    break;
            }
            
        } catch (Exception e) {
            log.error("统计更新任务失败: userId={}", task.getUserId(), e);
        }
    }

    @Override
    @Async
    public void processDataSyncTask(RedisQueueUtil.DataSyncTask task) {
        log.debug("开始处理数据同步任务: table={}, recordId={}, operation={}", 
                task.getTableName(), task.getRecordId(), task.getOperation());
        
        try {
            switch (task.getOperation()) {
                case "INSERT":
                case "UPDATE":
                    // 处理插入和更新操作的缓存同步
                    handleCacheSync(task.getTableName(), task.getRecordId(), task.getOperation());
                    break;
                case "DELETE":
                    // 处理删除操作的缓存清理
                    handleCacheDelete(task.getTableName(), task.getRecordId());
                    break;
                default:
                    log.warn("未知的数据同步操作: {}", task.getOperation());
                    break;
            }
            
        } catch (Exception e) {
            log.error("数据同步任务失败: table={}, recordId={}", 
                    task.getTableName(), task.getRecordId(), e);
        }
    }

    /**
     * 解析区块号
     */
    private Long parseBlockNumber(String blockNumberStr) {
        if (blockNumberStr.startsWith("0x")) {
            return Long.parseLong(blockNumberStr.substring(2), 16);
        } else {
            return Long.valueOf(blockNumberStr);
        }
    }

    /**
     * 清除相关缓存
     */
    private void clearRelatedCache(RedisQueueUtil.BlockchainTask task) {
        // 清除文件哈希缓存，触发重新查询
        redisCacheUtil.cacheFileHashExists(task.getFileHash(), true);
    }

    /**
     * 处理缓存同步
     */
    private void handleCacheSync(String tableName, Long recordId, String operation) {
        if ("file_evidence".equals(tableName)) {
            // 处理存证表的缓存同步
            log.debug("同步存证缓存: recordId={}, operation={}", recordId, operation);
        } else if ("users".equals(tableName)) {
            // 处理用户表的缓存同步
            redisCacheUtil.deleteCachedUserInfo(recordId);
            log.debug("同步用户缓存: recordId={}, operation={}", recordId, operation);
        }
    }

    /**
     * 处理缓存删除
     */
    private void handleCacheDelete(String tableName, Long recordId) {
        if ("file_evidence".equals(tableName)) {
            // 存证删除时的缓存清理
            log.debug("清理存证缓存: recordId={}", recordId);
        } else if ("users".equals(tableName)) {
            // 用户删除时的缓存清理
            redisCacheUtil.deleteCachedUserInfo(recordId);
            redisCacheUtil.deleteCachedUserStats(recordId);
            log.debug("清理用户缓存: recordId={}", recordId);
        }
    }
}