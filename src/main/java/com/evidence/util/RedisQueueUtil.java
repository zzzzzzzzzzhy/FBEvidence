package com.evidence.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis队列工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisQueueUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // ================================ 基础队列操作 ================================

    /**
     * 向队列左端推送消息
     */
    public boolean leftPush(String queueKey, Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            Long result = redisTemplate.opsForList().leftPush(queueKey, jsonMessage);
            log.debug("向队列推送消息: queue={}, size={}", queueKey, result);
            return result != null && result > 0;
        } catch (JsonProcessingException e) {
            log.error("序列化消息失败: queue={}", queueKey, e);
            return false;
        } catch (Exception e) {
            log.error("推送消息到队列失败: queue={}", queueKey, e);
            return false;
        }
    }

    /**
     * 向队列右端推送消息
     */
    public boolean rightPush(String queueKey, Object message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            Long result = redisTemplate.opsForList().rightPush(queueKey, jsonMessage);
            log.debug("向队列推送消息: queue={}, size={}", queueKey, result);
            return result != null && result > 0;
        } catch (JsonProcessingException e) {
            log.error("序列化消息失败: queue={}", queueKey, e);
            return false;
        } catch (Exception e) {
            log.error("推送消息到队列失败: queue={}", queueKey, e);
            return false;
        }
    }

    /**
     * 从队列右端弹出消息（阻塞）
     */
    public String blockingRightPop(String queueKey, long timeout, TimeUnit timeUnit) {
        try {
            Object result = redisTemplate.opsForList().rightPop(queueKey, timeout, timeUnit);
            if (result != null) {
                log.debug("从队列弹出消息: queue={}", queueKey);
                return result.toString();
            }
            return null;
        } catch (Exception e) {
            log.error("从队列弹出消息失败: queue={}", queueKey, e);
            return null;
        }
    }

    /**
     * 从队列右端弹出消息（非阻塞）
     */
    public String rightPop(String queueKey) {
        try {
            Object result = redisTemplate.opsForList().rightPop(queueKey);
            if (result != null) {
                log.debug("从队列弹出消息: queue={}", queueKey);
                return result.toString();
            }
            return null;
        } catch (Exception e) {
            log.error("从队列弹出消息失败: queue={}", queueKey, e);
            return null;
        }
    }

    /**
     * 获取队列长度
     */
    public long getQueueSize(String queueKey) {
        try {
            Long size = redisTemplate.opsForList().size(queueKey);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("获取队列长度失败: queue={}", queueKey, e);
            return 0;
        }
    }

    // ================================ 业务相关队列 ================================

    /**
     * 添加消息到队列（通用方法）
     */
    public boolean addToQueue(String queueKey, Object message) {
        return rightPush(queueKey, message);
    }

    /**
     * 推送区块链上链任务
     */
    public boolean pushBlockchainTask(BlockchainTask task) {
        String queueKey = getBlockchainQueueKey();
        return rightPush(queueKey, task);
    }

    /**
     * 弹出区块链上链任务
     */
    public BlockchainTask popBlockchainTask() {
        String queueKey = getBlockchainQueueKey();
        String taskJson = rightPop(queueKey);
        if (taskJson != null) {
            try {
                return objectMapper.readValue(taskJson, BlockchainTask.class);
            } catch (JsonProcessingException e) {
                log.error("反序列化区块链任务失败: {}", taskJson, e);
            }
        }
        return null;
    }

    /**
     * 推送文件处理任务
     */
    public boolean pushFileProcessTask(FileProcessTask task) {
        String queueKey = getFileProcessQueueKey();
        return rightPush(queueKey, task);
    }

    /**
     * 弹出文件处理任务
     */
    public FileProcessTask popFileProcessTask() {
        String queueKey = getFileProcessQueueKey();
        String taskJson = rightPop(queueKey);
        if (taskJson != null) {
            try {
                return objectMapper.readValue(taskJson, FileProcessTask.class);
            } catch (JsonProcessingException e) {
                log.error("反序列化文件处理任务失败: {}", taskJson, e);
            }
        }
        return null;
    }

    /**
     * 推送统计更新任务
     */
    public boolean pushStatsUpdateTask(StatsUpdateTask task) {
        String queueKey = getStatsUpdateQueueKey();
        return rightPush(queueKey, task);
    }

    /**
     * 弹出统计更新任务
     */
    public StatsUpdateTask popStatsUpdateTask() {
        String queueKey = getStatsUpdateQueueKey();
        String taskJson = rightPop(queueKey);
        if (taskJson != null) {
            try {
                return objectMapper.readValue(taskJson, StatsUpdateTask.class);
            } catch (JsonProcessingException e) {
                log.error("反序列化统计更新任务失败: {}", taskJson, e);
            }
        }
        return null;
    }

    /**
     * 推送数据同步任务
     */
    public boolean pushDataSyncTask(DataSyncTask task) {
        String queueKey = getDataSyncQueueKey();
        return rightPush(queueKey, task);
    }

    /**
     * 弹出数据同步任务
     */
    public DataSyncTask popDataSyncTask() {
        String queueKey = getDataSyncQueueKey();
        String taskJson = rightPop(queueKey);
        if (taskJson != null) {
            try {
                return objectMapper.readValue(taskJson, DataSyncTask.class);
            } catch (JsonProcessingException e) {
                log.error("反序列化数据同步任务失败: {}", taskJson, e);
            }
        }
        return null;
    }

    // ================================ Key生成方法 ================================

    private String getBlockchainQueueKey() {
        return "blockchain:upload:queue";
    }

    private String getFileProcessQueueKey() {
        return "file:process:queue";
    }

    private String getStatsUpdateQueueKey() {
        return "stats:update:queue";
    }

    private String getDataSyncQueueKey() {
        return "data:sync:queue";
    }

    // ================================ 任务实体类 ================================

    public static class BlockchainTask {
        private Long evidenceId;
        private String fileHash;
        private String fileName;
        private String uploader;
        private Long fileSize;
        private String description;
        private Integer retryCount = 0;
        private Long createTime = System.currentTimeMillis();

        // 构造函数
        public BlockchainTask() {}

        public BlockchainTask(Long evidenceId, String fileHash, String fileName, 
                            String uploader, Long fileSize, String description) {
            this.evidenceId = evidenceId;
            this.fileHash = fileHash;
            this.fileName = fileName;
            this.uploader = uploader;
            this.fileSize = fileSize;
            this.description = description;
        }

        // Getters and Setters
        public Long getEvidenceId() { return evidenceId; }
        public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }

        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getUploader() { return uploader; }
        public void setUploader(String uploader) { this.uploader = uploader; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Integer getRetryCount() { return retryCount; }
        public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
    }

    public static class FileProcessTask {
        private Long evidenceId;
        private String filePath;
        private String operation; // HASH_CALCULATE, VALIDATE, etc.
        private Integer retryCount = 0;
        private Long createTime = System.currentTimeMillis();

        // 构造函数
        public FileProcessTask() {}

        public FileProcessTask(Long evidenceId, String filePath, String operation) {
            this.evidenceId = evidenceId;
            this.filePath = filePath;
            this.operation = operation;
        }

        // Getters and Setters
        public Long getEvidenceId() { return evidenceId; }
        public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }

        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public Integer getRetryCount() { return retryCount; }
        public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
    }

    public static class StatsUpdateTask {
        private Long userId;
        private String operation; // UPDATE_USER_STATS, UPDATE_SYSTEM_STATS, etc.
        private Long createTime = System.currentTimeMillis();

        // 构造函数
        public StatsUpdateTask() {}

        public StatsUpdateTask(Long userId, String operation) {
            this.userId = userId;
            this.operation = operation;
        }

        // Getters and Setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
    }

    public static class DataSyncTask {
        private String tableName;
        private Long recordId;
        private String operation; // INSERT, UPDATE, DELETE
        private Long createTime = System.currentTimeMillis();

        // 构造函数
        public DataSyncTask() {}

        public DataSyncTask(String tableName, Long recordId, String operation) {
            this.tableName = tableName;
            this.recordId = recordId;
            this.operation = operation;
        }

        // Getters and Setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }

        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
    }
}