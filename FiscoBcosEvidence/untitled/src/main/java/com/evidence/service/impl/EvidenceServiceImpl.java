package com.evidence.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evidence.common.Constants;
import com.evidence.dto.FileEvidenceBatchUploadRequest;
import com.evidence.dto.FileEvidenceUploadRequest;
import com.evidence.dto.QueryRequest;
import com.evidence.dto.UploadRequest;
import com.evidence.entity.FileEvidence;
import com.evidence.entity.FileEvidenceGroup;
import com.evidence.entity.FileEvidenceOperationLog;
import com.evidence.entity.FileEvidenceProject;
import com.evidence.entity.User;
import com.evidence.service.FileEvidenceManagementService;
import com.evidence.mapper.EvidenceMapper;
import com.evidence.service.BlockchainService;
import com.evidence.service.EvidenceService;
import com.evidence.service.UserService;
import com.evidence.util.FileUtil;
import com.evidence.util.HashUtil;
import com.evidence.util.RedisCacheUtil;
import com.evidence.util.RedisLockUtil;
import com.evidence.util.RedisQueueUtil;
import com.evidence.vo.EvidenceResponse;
import com.evidence.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceServiceImpl extends ServiceImpl<EvidenceMapper, FileEvidence> implements EvidenceService {

    private final UserService userService;
    private final BlockchainService blockchainService;
    private final EvidenceMapper evidenceMapper;
    private final FileUtil fileUtil;
    private final RedisCacheUtil redisCacheUtil;
    private final RedisLockUtil redisLockUtil;
    private final RedisQueueUtil redisQueueUtil;
    private final FileEvidenceManagementService managementService;

    @Override
    @Transactional
    public EvidenceResponse uploadEvidence(UploadRequest uploadRequest) {
        log.info("=== Service: 开始处理文件上传 ===");
        try {
            log.info("Service: 获取当前用户信息");
            User currentUser = userService.getCurrentUser();
            log.info("Service: 当前用户: id={}, username={}, did={}", 
                    currentUser.getId(), currentUser.getUsername(), currentUser.getDid());

            final String contentType = uploadRequest.getContentType();
            
            // 根据内容类型处理
            log.info("Service: 开始处理文件保存, contentType={}", contentType);
            
            final String fileHash;
            final String fileName;
            final Long fileSize;
            
            if (Constants.CONTENT_TYPE_TEXT.equals(contentType)) {
                log.info("Service: 处理文字内容存证");
                // 文字内容处理
                byte[] textBytes = uploadRequest.getFile().getBytes();
                
                // 计算文字内容哈希
                fileHash = HashUtil.calculateHash(textBytes, uploadRequest.getHashAlgorithm());
                
                // 设置文件信息
                fileName = uploadRequest.getFile().getOriginalFilename();
                fileSize = uploadRequest.getFile().getSize();
                
            } else {
                log.info("Service: 处理文件存证");
                // 先计算文件哈希
                fileHash = HashUtil.calculateHash(uploadRequest.getFile().getBytes(),
                        uploadRequest.getHashAlgorithm());
                fileName = uploadRequest.getFile().getOriginalFilename();
                fileSize = uploadRequest.getFile().getSize();
            }
            log.info("Service: 文件处理完成, fileName={}, fileSize={}, fileHash={}", 
                    fileName, fileSize, fileHash);

            // 使用分布式锁防止重复上传相同哈希的文件
            String uploadLockKey = redisLockUtil.getUploadLockKey(currentUser.getId(), fileHash);
            
            return redisLockUtil.executeWithLock(uploadLockKey, 30, () -> {
                String filePath;
                // 检查Redis缓存中是否已存在该哈希
                Boolean hashExists = redisCacheUtil.getFileHashExists(fileHash);
                if (hashExists == null) {
                    // 缓存中没有，查询数据库
                    LambdaQueryWrapper<FileEvidence> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(FileEvidence::getFileHash, fileHash);
                    hashExists = count(wrapper) > 0;
                    // 缓存查询结果
                    redisCacheUtil.cacheFileHashExists(fileHash, hashExists);
                }
                
                if (hashExists) {
                    throw new RuntimeException("内容已存在，哈希值重复");
                }

                // 根据内容类型保存文件
                if (Constants.CONTENT_TYPE_TEXT.equals(contentType)) {
                    // 根据用户DID保存文字内容为文件到MinIO
                    log.info("Service: 调用FileUtil.saveFileWithUserDID() for text content");
                    filePath = fileUtil.saveFileWithUserDID(uploadRequest.getFile(), currentUser.getDid());
                    log.info("Service: 文字内容保存成功, filePath={}", filePath);
                } else {
                    // 根据用户DID处理文件上传到MinIO
                    log.info("Service: 调用FileUtil.saveFileWithUserDID() for file");
                    filePath = fileUtil.saveFileWithUserDID(uploadRequest.getFile(), currentUser.getDid());
                    log.info("Service: 文件保存成功, filePath={}", filePath);
                }

                // 创建存证记录
                FileEvidence evidence = new FileEvidence();
                evidence.setUserId(currentUser.getId());
                evidence.setFileName(fileName);
                evidence.setFileHash(fileHash);
                evidence.setFileSize(fileSize);
                evidence.setFilePath(filePath);
                evidence.setHashAlgorithm(uploadRequest.getHashAlgorithm());
                evidence.setDescription(uploadRequest.getDescription());
                evidence.setContentType(contentType);
                evidence.setChainStatus(Constants.CHAIN_STATUS_PENDING);

                // 保存到数据库
                save(evidence);

                // 更新Redis缓存
                redisCacheUtil.cacheFileHashExists(fileHash, true);
                
                // 删除用户统计缓存，触发重新计算
                redisCacheUtil.deleteCachedUserStats(currentUser.getId());

                // 推送区块链上链任务到队列
                RedisQueueUtil.BlockchainTask blockchainTask = new RedisQueueUtil.BlockchainTask(
                    evidence.getId(),
                    fileHash,
                    fileName,
                    currentUser.getUsername(),
                    fileSize,
                    uploadRequest.getDescription()
                );
                redisQueueUtil.pushBlockchainTask(blockchainTask);

                // 推送统计更新任务
                RedisQueueUtil.StatsUpdateTask statsTask = new RedisQueueUtil.StatsUpdateTask(
                    currentUser.getId(),
                    "UPDATE_USER_STATS"
                );
                redisQueueUtil.pushStatsUpdateTask(statsTask);

                log.info("文件上传成功，已推送上链任务到队列: evidenceId={}", evidence.getId());

                // 转换为响应对象
                EvidenceResponse response = new EvidenceResponse();
                BeanUtils.copyProperties(evidence, response);
                return response;
            });

        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public PageResponse<EvidenceResponse> queryEvidence(QueryRequest queryRequest) {
        User currentUser = userService.getCurrentUser();

        Page<FileEvidence> page = new Page<>(queryRequest.getCurrent(), queryRequest.getSize());

        Page<FileEvidence> resultPage = (Page<FileEvidence>) evidenceMapper.selectEvidencePage(
                page,
                currentUser.getId(),
                queryRequest.getFileName(),
                queryRequest.getFileHash(),
                queryRequest.getTransactionHash(),
                queryRequest.getBlockNumber(),
                queryRequest.getChainStatus(),
                queryRequest.getStartDate(),
                queryRequest.getEndDate(),
                queryRequest.getContentType(),
                null // 普通查询不过滤项目
        );

        List<EvidenceResponse> responses = resultPage.getRecords().stream()
                .map(evidence -> {
                    EvidenceResponse response = new EvidenceResponse();
                    BeanUtils.copyProperties(evidence, response);
                    return response;
                })
                .collect(Collectors.toList());

        return new PageResponse<>(responses, resultPage.getTotal(),
                resultPage.getSize(), resultPage.getCurrent());
    }

    @Override
    public EvidenceResponse getEvidenceById(Long id) {
        User currentUser = userService.getCurrentUser();

        FileEvidence evidence = getById(id);
        if (evidence == null || !evidence.getUserId().equals(currentUser.getId())) {
            throw new RuntimeException("存证记录不存在");
        }

        EvidenceResponse response = new EvidenceResponse();
        BeanUtils.copyProperties(evidence, response);
        return response;
    }

    @Override
    public EvidenceResponse getEvidenceByHash(String fileHash) {
        LambdaQueryWrapper<FileEvidence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEvidence::getFileHash, fileHash);

        FileEvidence evidence = getOne(wrapper);
        if (evidence == null) {
            throw new RuntimeException("存证记录不存在");
        }

        EvidenceResponse response = new EvidenceResponse();
        BeanUtils.copyProperties(evidence, response);
        return response;
    }

    @Override
    public EvidenceResponse getEvidenceByTransactionHash(String transactionHash) {
        log.info("根据交易哈希查询存证: {}", transactionHash);
        LambdaQueryWrapper<FileEvidence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEvidence::getTransactionHash, transactionHash);

        FileEvidence evidence = getOne(wrapper);
        if (evidence == null) {
            throw new RuntimeException("未找到该交易对应的存证记录");
        }

        EvidenceResponse response = new EvidenceResponse();
        BeanUtils.copyProperties(evidence, response);
        return response;
    }

    @Override
    public List<EvidenceResponse> getEvidenceByBlockNumber(Long blockNumber) {
        log.info("根据区块号查询存证: {}", blockNumber);
        LambdaQueryWrapper<FileEvidence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEvidence::getBlockNumber, blockNumber);

        List<FileEvidence> evidenceList = list(wrapper);
        if (evidenceList == null || evidenceList.isEmpty()) {
            throw new RuntimeException("该区块中未找到存证记录");
        }

        return evidenceList.stream()
                .map(evidence -> {
                    EvidenceResponse response = new EvidenceResponse();
                    BeanUtils.copyProperties(evidence, response);
                    return response;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public boolean verifyEvidence(String fileHash) {
        // 检查数据库中是否存在
        LambdaQueryWrapper<FileEvidence> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEvidence::getFileHash, fileHash);
        FileEvidence evidence = getOne(wrapper);

        if (evidence == null) {
            return false;
        }

        // 验证区块链
        return blockchainService.verifyEvidence(fileHash);
    }

    @Override
    public java.util.Map<String, Object> getStatistics() {
        try {
            User currentUser = userService.getCurrentUser();
            
            // 先尝试从Redis缓存获取统计信息
            java.util.Map<String, Object> statistics = redisCacheUtil.getCachedUserStats(currentUser.getId());
            if (statistics != null) {
                log.debug("从Redis缓存获取用户统计信息: userId={}", currentUser.getId());
                return statistics;
            }
            
            // 缓存中没有，查询数据库并缓存
            log.debug("缓存中没有统计信息，查询数据库: userId={}", currentUser.getId());
            statistics = new java.util.HashMap<>();
            
            // 获取当前用户的总文件数
            LambdaQueryWrapper<FileEvidence> totalWrapper = new LambdaQueryWrapper<>();
            totalWrapper.eq(FileEvidence::getUserId, currentUser.getId());
            long totalFiles = count(totalWrapper);
            
            // 获取今日上传数（当前日期的上传数）
            LambdaQueryWrapper<FileEvidence> todayWrapper = new LambdaQueryWrapper<>();
            todayWrapper.eq(FileEvidence::getUserId, currentUser.getId());
            java.time.LocalDateTime startOfDay = java.time.LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            java.time.LocalDateTime endOfDay = startOfDay.plusDays(1);
            todayWrapper.between(FileEvidence::getCreatedAt, startOfDay, endOfDay);
            long todayFiles = count(todayWrapper);
            
            // 获取成功上链的文件数
            LambdaQueryWrapper<FileEvidence> successWrapper = new LambdaQueryWrapper<>();
            successWrapper.eq(FileEvidence::getUserId, currentUser.getId());
            successWrapper.eq(FileEvidence::getChainStatus, Constants.CHAIN_STATUS_SUCCESS);
            long successFiles = count(successWrapper);
            
            // 计算成功率
            String successRate = totalFiles > 0 ? 
                String.format("%.1f%%", (double) successFiles / totalFiles * 100) : "0%";
            
            statistics.put("totalFiles", totalFiles);
            statistics.put("todayFiles", todayFiles);
            statistics.put("successRate", successRate);
            statistics.put("successFiles", successFiles);
            statistics.put("failedFiles", totalFiles - successFiles);
            
            // 缓存统计结果
            redisCacheUtil.cacheUserStats(currentUser.getId(), statistics);
            
            return statistics;
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            throw new RuntimeException("获取统计信息失败: " + e.getMessage());
        }
    }

    @Override
    public void testMinIOConnection() {
        log.info("=== Service: 测试MinIO连接 ===");
        try {
            // 通过FileUtil测试连接
            fileUtil.testMinIOConnection();
            log.info("Service: MinIO连接测试成功");
        } catch (Exception e) {
            log.error("Service: MinIO连接测试失败", e);
            throw new RuntimeException("MinIO连接失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void storeEvidence(FileEvidence evidence) {
        log.info("=== Service: 开始存储文件存证 ===");
        try {
            // 保存到数据库
            save(evidence);
            log.info("文件存证保存到数据库成功: id={}, fileName={}", evidence.getId(), evidence.getFileName());
            
            // 获取用户信息
            String uploaderName = "User-" + evidence.getUserId(); // 默认用户名格式
            try {
                User user = userService.getById(evidence.getUserId());
                if (user != null && user.getUsername() != null) {
                    uploaderName = user.getUsername();
                }
            } catch (Exception e) {
                log.warn("获取用户名失败，使用默认格式: userId={}", evidence.getUserId());
            }
            
            // 构造区块链上链任务
            RedisQueueUtil.BlockchainTask blockchainTask = new RedisQueueUtil.BlockchainTask(
                evidence.getId(),
                evidence.getFileHash(),
                evidence.getFileName(),
                uploaderName, // 使用实际用户名
                evidence.getFileSize(),
                evidence.getDescription()
            );
            
            // 推送区块链上链任务到队列
            redisQueueUtil.pushBlockchainTask(blockchainTask);
            log.info("文件存证已推送上链任务到队列: evidenceId={}, fileName={}", evidence.getId(), evidence.getFileName());
            
        } catch (Exception e) {
            log.error("存储文件存证失败: fileName={}", evidence.getFileName(), e);
            throw new RuntimeException("存储文件存证失败: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public EvidenceResponse uploadFileEvidence(FileEvidenceUploadRequest uploadRequest) {
        log.info("=== Service: 开始处理文件存证上传 ===");
        try {
            User currentUser = userService.getCurrentUser();
            log.info("Service: 当前用户: id={}, username={}, did={}", 
                    currentUser.getId(), currentUser.getUsername(), currentUser.getDid());

            // 验证项目权限
            FileEvidenceProject project = managementService.getProjectById(uploadRequest.getProjectId());
            if (project == null || project.getStatus() != FileEvidenceProject.Status.ENABLED) {
                throw new RuntimeException("项目不存在或已禁用");
            }

            final String contentType = uploadRequest.getContentType();
            final String fileHash;
            final String fileName;
            final Long fileSize;
            
            // 计算文件哈希
            fileHash = HashUtil.calculateHash(uploadRequest.getFile().getBytes(), uploadRequest.getHashAlgorithm());
            fileName = uploadRequest.getFile().getOriginalFilename();
            fileSize = uploadRequest.getFile().getSize();
            
            log.info("Service: 文件处理完成, fileName={}, fileSize={}, fileHash={}", 
                    fileName, fileSize, fileHash);

            // 使用分布式锁防止重复上传相同哈希的文件
            String uploadLockKey = redisLockUtil.getUploadLockKey(currentUser.getId(), fileHash);
            
            return redisLockUtil.executeWithLock(uploadLockKey, 30, () -> {
                String filePath;
                // 检查Redis缓存中是否已存在该哈希
                Boolean hashExists = redisCacheUtil.getFileHashExists(fileHash);
                if (hashExists == null) {
                    // 缓存中没有，查询数据库
                    LambdaQueryWrapper<FileEvidence> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(FileEvidence::getFileHash, fileHash);
                    hashExists = count(wrapper) > 0;
                    // 缓存查询结果
                    redisCacheUtil.cacheFileHashExists(fileHash, hashExists);
                }
                
                if (hashExists) {
                    throw new RuntimeException("哈希值已存在，不可重复上传！");
                }

                // 根据用户DID保存文件到MinIO
                log.info("Service: 调用FileUtil.saveFileWithUserDID() for file evidence");
                filePath = fileUtil.saveFileWithUserDID(uploadRequest.getFile(), currentUser.getDid());
                log.info("Service: 文件保存成功, filePath={}", filePath);

                // 创建存证记录
                FileEvidence evidence = new FileEvidence();
                evidence.setUserId(currentUser.getId());
                evidence.setProjectId(uploadRequest.getProjectId());
                evidence.setFileName(fileName);
                evidence.setFileHash(fileHash);
                evidence.setFileSize(fileSize);
                evidence.setFilePath(filePath);
                evidence.setHashAlgorithm(uploadRequest.getHashAlgorithm());
                evidence.setDescription(uploadRequest.getDescription());
                evidence.setCommitMessage(uploadRequest.getCommitMessage());
                evidence.setContentType(contentType);
                evidence.setChainStatus(Constants.CHAIN_STATUS_PENDING);

                // 保存到数据库
                save(evidence);

                // 更新Redis缓存
                redisCacheUtil.cacheFileHashExists(fileHash, true);
                
                // 删除用户统计缓存，触发重新计算
                redisCacheUtil.deleteCachedUserStats(currentUser.getId());

                // 推送区块链上链任务到队列
                RedisQueueUtil.BlockchainTask blockchainTask = new RedisQueueUtil.BlockchainTask(
                    evidence.getId(),
                    fileHash,
                    fileName,
                    currentUser.getUsername(),
                    fileSize,
                    uploadRequest.getDescription()
                );
                redisQueueUtil.pushBlockchainTask(blockchainTask);

                // 推送统计更新任务
                RedisQueueUtil.StatsUpdateTask statsTask = new RedisQueueUtil.StatsUpdateTask(
                    currentUser.getId(),
                    "UPDATE_USER_STATS"
                );
                redisQueueUtil.pushStatsUpdateTask(statsTask);

                // 记录操作日志
                managementService.logOperation(
                    FileEvidenceOperationLog.OperationType.UPLOAD_FILE,
                    FileEvidenceOperationLog.TargetType.FILE,
                    evidence.getId(),
                    fileName,
                    "上传文件存证：" + fileName + "，项目：" + project.getProjectName(),
                    true,
                    null
                );

                log.info("文件存证上传成功，已推送上链任务到队列: evidenceId={}", evidence.getId());

                // 转换为响应对象
                EvidenceResponse response = new EvidenceResponse();
                BeanUtils.copyProperties(evidence, response);
                return response;
            });

        } catch (Exception e) {
            log.error("文件存证上传失败", e);
            throw new RuntimeException("文件存证上传失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public List<EvidenceResponse> uploadFileEvidenceBatch(FileEvidenceBatchUploadRequest uploadRequest) {
        log.info("=== Service: 开始处理批量文件存证上传 ===");
        try {
            User currentUser = userService.getCurrentUser();
            log.info("Service: 当前用户: id={}, username={}, did={}", 
                    currentUser.getId(), currentUser.getUsername(), currentUser.getDid());

            // 验证项目权限
            FileEvidenceProject project = managementService.getProjectById(uploadRequest.getProjectId());
            if (project == null || project.getStatus() != FileEvidenceProject.Status.ENABLED) {
                throw new RuntimeException("项目不存在或已禁用");
            }

            List<EvidenceResponse> results = new ArrayList<>();
            
            for (MultipartFile file : uploadRequest.getFiles()) {
                try {
                    FileEvidenceUploadRequest singleRequest = new FileEvidenceUploadRequest();
                    singleRequest.setProjectId(uploadRequest.getProjectId());
                    singleRequest.setFile(file);
                    singleRequest.setHashAlgorithm(uploadRequest.getHashAlgorithm());
                    singleRequest.setCommitMessage(uploadRequest.getCommitMessage());
                    singleRequest.setContentType(uploadRequest.getContentType());
                    singleRequest.setDescription("批量上传：" + uploadRequest.getCommitMessage());
                    
                    EvidenceResponse result = uploadFileEvidence(singleRequest);
                    results.add(result);
                    log.info("批量上传文件成功: fileName={}, evidenceId={}", 
                            file.getOriginalFilename(), result.getId());
                    
                } catch (Exception e) {
                    log.error("批量上传文件失败: fileName={}, error={}", 
                            file.getOriginalFilename(), e.getMessage());
                    // 继续处理下一个文件，不中断整个批量上传过程
                }
            }

            // 记录批量操作日志
            managementService.logOperation(
                FileEvidenceOperationLog.OperationType.BATCH_UPLOAD_FILE,
                FileEvidenceOperationLog.TargetType.FILE,
                null,
                "批量上传",
                String.format("批量上传文件存证，成功：%d个，失败：%d个，项目：%s", 
                        results.size(), 
                        uploadRequest.getFiles().length - results.size(),
                        project.getProjectName()),
                true,
                null
            );

            log.info("批量文件存证上传完成，成功：{}个，失败：{}个", 
                    results.size(), uploadRequest.getFiles().length - results.size());
            
            return results;

        } catch (Exception e) {
            log.error("批量文件存证上传失败", e);
            throw new RuntimeException("批量文件存证上传失败: " + e.getMessage());
        }
    }

    @Override
    public PageResponse<EvidenceResponse> queryFileEvidence(QueryRequest queryRequest, Long projectId) {
        User currentUser = userService.getCurrentUser();

        Page<FileEvidence> page = new Page<>(queryRequest.getCurrent(), queryRequest.getSize());

        Page<FileEvidence> resultPage = (Page<FileEvidence>) evidenceMapper.selectFileEvidencePage(
                page,
                currentUser.getId(),
                queryRequest.getFileName(),
                queryRequest.getFileHash(),
                queryRequest.getTransactionHash(),
                queryRequest.getBlockNumber(),
                queryRequest.getChainStatus(),
                queryRequest.getStartDate(),
                queryRequest.getEndDate(),
                queryRequest.getContentType(),
                projectId // 新增项目ID过滤
        );

        List<EvidenceResponse> responses = resultPage.getRecords().stream()
                .map(evidence -> {
                    EvidenceResponse response = new EvidenceResponse();
                    BeanUtils.copyProperties(evidence, response);

                    // 获取项目和分组信息
                    if (evidence.getProjectId() != null) {
                        try {
                            FileEvidenceProject project = managementService.getProjectById(evidence.getProjectId());
                            if (project != null) {
                                response.setProjectName(project.getProjectName());
                                response.setProjectId(project.getId());

                                // 获取分组信息
                                if (project.getGroupId() != null) {
                                    FileEvidenceGroup group = managementService.getGroupById(project.getGroupId());
                                    if (group != null) {
                                        response.setGroupName(group.getGroupName());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("获取项目信息失败: projectId={}, error={}", evidence.getProjectId(), e.getMessage());
                        }
                    }

                    return response;
                })
                .collect(Collectors.toList());

        return new PageResponse<>(responses, resultPage.getTotal(),
                resultPage.getSize(), resultPage.getCurrent());
    }

    @Override
    public Map<String, Object> getFileEvidenceStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 获取文件总数
            Long totalFiles = evidenceMapper.selectCount(null);
            stats.put("totalFiles", totalFiles);

            // 获取今日上传数量
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

            LambdaQueryWrapper<FileEvidence> todayQuery = new LambdaQueryWrapper<>();
            todayQuery.between(FileEvidence::getCreatedAt, startOfDay, endOfDay);
            Long todayUpload = evidenceMapper.selectCount(todayQuery);
            stats.put("todayUpload", todayUpload);

            // 获取成功上链数量
            LambdaQueryWrapper<FileEvidence> successQuery = new LambdaQueryWrapper<>();
            successQuery.eq(FileEvidence::getChainStatus, 1); // 1表示成功
            Long successCount = evidenceMapper.selectCount(successQuery);
            stats.put("successCount", successCount);

        } catch (Exception e) {
            log.error("获取文件存证统计失败", e);
            stats.put("totalFiles", 0);
            stats.put("todayUpload", 0);
            stats.put("successCount", 0);
        }

        return stats;
    }
}