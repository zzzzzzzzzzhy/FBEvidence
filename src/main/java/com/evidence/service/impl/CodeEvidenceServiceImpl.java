package com.evidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evidence.common.Constants;
import com.evidence.dto.BatchCodeUploadRequest;
import com.evidence.dto.CodeUploadRequest;
import com.evidence.entity.FileEvidence;
import com.evidence.entity.GitBranch;
import com.evidence.entity.GitRepository;
import com.evidence.mapper.EvidenceMapper;
import com.evidence.mapper.GitBranchMapper;
import com.evidence.mapper.GitRepositoryMapper;
import com.evidence.service.CodeEvidenceService;
import com.evidence.service.EvidenceService;
import com.evidence.util.FileUtil;
import com.evidence.util.NativeGitUtil;
import com.evidence.util.RedisCacheUtil;
import com.evidence.util.NativeGitUtil.GitCommitInfo;
import com.evidence.util.NativeGitUtil.GitFileInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeEvidenceServiceImpl implements CodeEvidenceService {

    private final NativeGitUtil nativeGitUtil;
    private final EvidenceService evidenceService;
    private final GitRepositoryMapper gitRepositoryMapper;
    private final GitBranchMapper gitBranchMapper;
    private final EvidenceMapper evidenceMapper;
    private final RedisCacheUtil redisCacheUtil;

    @Override
    @Transactional
    public GitRepository createRepository(Long userId, String groupName, String projectName, String description) {
        GitRepository existingRepo = gitRepositoryMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitRepository>()
                .eq(GitRepository::getUserId, userId)
                .eq(GitRepository::getGroupName, groupName)
                .eq(GitRepository::getProjectName, projectName)
        );
        
        if (existingRepo != null) {
            throw new IllegalArgumentException("Repository already exists: " + groupName + "/" + projectName);
        }

        try {
            log.info("Starting repository creation for: {}/{}", groupName, projectName);
            
            // Step 1: Initialize Git repository with main branch
            String repositoryPath;
            try {
                repositoryPath = nativeGitUtil.initRepository(groupName, projectName, description);
                log.info("Git repository initialized at: {}", repositoryPath);
            } catch (IllegalStateException e) {
                // Handle case where Git repository exists but database record doesn't
                if (e.getMessage().contains("Repository directory exists with valid Git data")) {
                    log.warn("Found existing Git repository without database record, attempting to clean up and retry");
                    
                    // Clean up the existing directory and retry once
                    cleanupOrphanedGitRepository(groupName, projectName);
                    
                    // Retry initialization
                    repositoryPath = nativeGitUtil.initRepository(groupName, projectName, description);
                    log.info("Git repository successfully re-initialized at: {}", repositoryPath);
                } else {
                    throw e;
                }
            }
            
            // Step 2: Create database record for repository (without commit validation)
            GitRepository repository = new GitRepository();
            repository.setUserId(userId);
            repository.setGroupName(groupName);
            repository.setProjectName(projectName);
            repository.setRepositoryPath(repositoryPath);
            repository.setDefaultBranch("main");
            repository.setDescription(description);
            repository.setStatus(1);
            repository.setValidationStatus(GitRepository.ValidationStatus.UNVALIDATED); // 设置为未验证状态
            repository.setCreatedAt(LocalDateTime.now());
            repository.setUpdatedAt(LocalDateTime.now());
            
            gitRepositoryMapper.insert(repository);
            log.info("Repository record created in database with ID: {}", repository.getId());
            
            // Step 3: Create initial main branch record (without commit info)
            GitBranch mainBranch = new GitBranch();
            mainBranch.setRepositoryId(repository.getId());
            mainBranch.setBranchName("main");
            mainBranch.setBaseBranch(null);
            mainBranch.setLastCommitHash(null); // 先不设置commit信息
            mainBranch.setLastCommitMessage(null);
            mainBranch.setLastCommitTime(null);
            mainBranch.setIsProtected(false);
            mainBranch.setCreatedAt(LocalDateTime.now());
            mainBranch.setUpdatedAt(LocalDateTime.now());
            
            gitBranchMapper.insert(mainBranch);
            log.info("Initial main branch record created for repository: {}/{}", groupName, projectName);
            
            log.info("Repository created successfully: {}/{} (validation deferred)", groupName, projectName);
            return repository;
            
        } catch (Exception e) {
            log.error("Failed to create repository: {}/{}, error: {}", groupName, projectName, e.getMessage(), e);
            
            // Clean up any partial state on failure
            cleanupFailedRepository(groupName, projectName);
            
            throw new RuntimeException("Failed to create repository: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void validateRepository(Long repositoryId) {
        GitRepository repository = gitRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            throw new IllegalArgumentException("Repository not found: " + repositoryId);
        }
        
        try {
            log.info("Validating repository: {}/{}", repository.getGroupName(), repository.getProjectName());
            
            // Step 1: Validate Git repository state
            validateRepositoryState(repository.getRepositoryPath(), "main");
            
            // Step 2: Get commit information
            GitCommitInfo latestCommit = nativeGitUtil.getLatestCommit(repository.getRepositoryPath(), "main");
            
            // Step 3: Update main branch with commit info
            GitBranch mainBranch = gitBranchMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GitBranch>()
                    .eq("repository_id", repositoryId)
                    .eq("branch_name", "main")
            );
            
            if (mainBranch != null) {
                mainBranch.setLastCommitHash(latestCommit.getCommitHash());
                mainBranch.setLastCommitMessage(latestCommit.getMessage());
                mainBranch.setLastCommitTime(latestCommit.getCommitTime());
                mainBranch.setUpdatedAt(LocalDateTime.now());
                gitBranchMapper.updateById(mainBranch);
            }
            
            // Step 4: Update repository validation status
            repository.setValidationStatus(GitRepository.ValidationStatus.VALIDATED);
            repository.setUpdatedAt(LocalDateTime.now());
            gitRepositoryMapper.updateById(repository);
            
            log.info("Repository validation completed: {}/{}", repository.getGroupName(), repository.getProjectName());
            
        } catch (Exception e) {
            log.error("Repository validation failed: {}/{}, error: {}", repository.getGroupName(), repository.getProjectName(), e.getMessage());
            
            // Update validation status to failed
            repository.setValidationStatus(GitRepository.ValidationStatus.VALIDATION_FAILED);
            repository.setUpdatedAt(LocalDateTime.now());
            gitRepositoryMapper.updateById(repository);
            
            throw new RuntimeException("Repository validation failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public GitBranch createBranch(Long repositoryId, String branchName, String baseBranch) {
        GitRepository repository = gitRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            throw new IllegalArgumentException("Repository not found: " + repositoryId);
        }

        // 确保仓库已验证
        if (repository.getValidationStatus() == null || repository.getValidationStatus() == GitRepository.ValidationStatus.UNVALIDATED) {
            log.info("Repository not validated yet, validating now: {}/{}", repository.getGroupName(), repository.getProjectName());
            validateRepository(repositoryId);
            // 重新查询更新后的仓库信息
            repository = gitRepositoryMapper.selectById(repositoryId);
        }
        
        if (repository.getValidationStatus() == GitRepository.ValidationStatus.VALIDATION_FAILED) {
            throw new IllegalStateException("Repository validation failed, cannot create branch");
        }

        GitBranch existingBranch = gitBranchMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitBranch>()
                .eq(GitBranch::getRepositoryId, repositoryId)
                .eq(GitBranch::getBranchName, branchName)
        );
        
        if (existingBranch != null) {
            throw new IllegalArgumentException("Branch already exists: " + branchName);
        }

        try {
            String createdBranch = nativeGitUtil.createBranch(repository.getRepositoryPath(), branchName, baseBranch);
            
            GitBranch branch = new GitBranch();
            branch.setRepositoryId(repositoryId);
            branch.setBranchName(branchName);
            branch.setBaseBranch(baseBranch);
            branch.setIsProtected(false);
            branch.setCreatedAt(LocalDateTime.now());
            branch.setUpdatedAt(LocalDateTime.now());
            
            // 获取最新commit信息
            try {
                GitCommitInfo latestCommit = nativeGitUtil.getLatestCommit(repository.getRepositoryPath(), branchName);
                branch.setLastCommitHash(latestCommit.getCommitHash());
                branch.setLastCommitMessage(latestCommit.getMessage());
                branch.setLastCommitTime(latestCommit.getCommitTime());
            } catch (Exception e) {
                log.warn("Failed to get commit info for new branch {}, will be updated later: {}", branchName, e.getMessage());
            }
            
            gitBranchMapper.insert(branch);
            
            log.info("Branch created successfully: {} in repository {}", branchName, repositoryId);
            return branch;
            
        } catch (Exception e) {
            log.error("Failed to create branch: {} in repository {}, error: {}", branchName, repositoryId, e.getMessage());
            throw new RuntimeException("Failed to create branch: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public FileEvidence uploadCode(Long userId, CodeUploadRequest request) {
        GitRepository repository = getOrCreateRepository(userId, request.getGroupName(), request.getProjectName(), request.getDescription());
        
        return commitCode(userId, repository.getId(), request.getBranch(), request.getFile(), 
                         request.getFile().getOriginalFilename(), "Upload: " + request.getFile().getOriginalFilename());
    }

    @Override
    @Transactional
    public FileEvidence commitCode(Long userId, Long repositoryId, String branchName, MultipartFile file, 
                                  String fileName, String commitMessage) {
        GitRepository repository = gitRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            throw new IllegalArgumentException("Repository not found: " + repositoryId);
        }

        if (!repository.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied to repository: " + repositoryId);
        }

        try {
            String content = new String(file.getBytes());
            String fileHash = FileUtil.calculateSHA256(file.getBytes());
            
            // 检查文件哈希是否已存在
            Boolean hashExists = redisCacheUtil.getFileHashExists(fileHash);
            if (hashExists == null) {
                // 缓存中没有，查询数据库
                LambdaQueryWrapper<FileEvidence> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(FileEvidence::getFileHash, fileHash);
                hashExists = evidenceService.count(wrapper) > 0;
                // 缓存查询结果
                redisCacheUtil.cacheFileHashExists(fileHash, hashExists);
            }
            
            if (hashExists) {
                throw new RuntimeException("哈希值已存在，不可重复上传！");
            }
            
            GitCommitInfo commitInfo = nativeGitUtil.addAndCommit(
                repository.getRepositoryPath(), 
                fileName, 
                content, 
                commitMessage,
                "User-" + userId,
                "user" + userId + "@evidence.local"
            );
            
            FileEvidence evidence = new FileEvidence();
            evidence.setUserId(userId);
            evidence.setFileName(fileName);
            evidence.setFileHash(fileHash);
            evidence.setFileSize(file.getSize());
            evidence.setFilePath(repository.getRepositoryPath() + "/" + fileName);
            evidence.setHashAlgorithm("SHA256");
            evidence.setContentType("CODE");
            evidence.setDescription("代码文件存证: " + fileName); // 设置描述信息
            
            evidence.setGitGroupName(repository.getGroupName());
            evidence.setGitProjectName(repository.getProjectName());
            evidence.setGitBranchName(branchName);
            evidence.setGitCommitHash(commitInfo.getCommitHash());
            evidence.setGitCommitMessage(commitInfo.getMessage());
            evidence.setGitAuthorName(commitInfo.getAuthorName());
            evidence.setGitAuthorEmail(commitInfo.getAuthorEmail());
            evidence.setGitCommitTime(commitInfo.getCommitTime());
            evidence.setGitRepositoryPath(repository.getRepositoryPath());
            evidence.setGitStatus(1); // 1: 本地成功
            evidence.setChainStatus(Constants.CHAIN_STATUS_PENDING); // 设置为待上链状态
            
            evidence.setCreatedAt(LocalDateTime.now());
            evidence.setUpdatedAt(LocalDateTime.now());
            
            evidenceService.storeEvidence(evidence);
            
            updateBranchInfo(repositoryId, branchName, commitInfo);
            
            log.info("Code committed successfully: {} in {}/{}/{}", fileName, 
                    repository.getGroupName(), repository.getProjectName(), branchName);
            return evidence;
            
        } catch (Exception e) {
            log.error("Failed to commit code: {}, error: {}", fileName, e.getMessage());
            throw new RuntimeException("Failed to commit code: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public List<FileEvidence> commitCodeBatch(Long userId, BatchCodeUploadRequest request) {
        GitRepository repository = gitRepositoryMapper.selectById(request.getRepositoryId());
        if (repository == null) {
            throw new IllegalArgumentException("Repository not found: " + request.getRepositoryId());
        }

        if (!repository.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied to repository: " + request.getRepositoryId());
        }

        if (request.getFiles() == null || request.getFiles().length == 0) {
            throw new IllegalArgumentException("No files provided for batch upload");
        }

        try {
            Map<String, String> fileContentMap = new HashMap<>();
            List<FileEvidence> evidenceList = new ArrayList<>();
            
            // 首先检查所有文件的哈希值是否重复
            for (MultipartFile file : request.getFiles()) {
                String content = new String(file.getBytes());
                String fileHash = FileUtil.calculateSHA256(file.getBytes());
                
                // 检查文件哈希是否已存在
                Boolean hashExists = redisCacheUtil.getFileHashExists(fileHash);
                if (hashExists == null) {
                    // 缓存中没有，查询数据库
                    LambdaQueryWrapper<FileEvidence> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(FileEvidence::getFileHash, fileHash);
                    hashExists = evidenceService.count(wrapper) > 0;
                    // 缓存查询结果
                    redisCacheUtil.cacheFileHashExists(fileHash, hashExists);
                }
                
                if (hashExists) {
                    throw new RuntimeException("文件 " + file.getOriginalFilename() + " 哈希值已存在，不可重复上传！");
                }
                
                fileContentMap.put(file.getOriginalFilename(), content);
            }
            
            GitCommitInfo commitInfo = nativeGitUtil.addAndCommitMultiple(
                repository.getRepositoryPath(),
                fileContentMap,
                request.getCommitMessage(),
                "User-" + userId,
                "user" + userId + "@evidence.local"
            );
            
            for (MultipartFile file : request.getFiles()) {
                String fileHash = FileUtil.calculateSHA256(file.getBytes());
                
                FileEvidence evidence = new FileEvidence();
                evidence.setUserId(userId);
                evidence.setFileName(file.getOriginalFilename());
                evidence.setFileHash(fileHash);
                evidence.setFileSize(file.getSize());
                evidence.setFilePath(repository.getRepositoryPath() + "/" + file.getOriginalFilename());
                evidence.setHashAlgorithm("SHA256");
                evidence.setContentType("CODE");
                evidence.setDescription("代码文件存证: " + file.getOriginalFilename()); // 设置描述信息
                
                evidence.setGitGroupName(repository.getGroupName());
                evidence.setGitProjectName(repository.getProjectName());
                evidence.setGitBranchName(request.getBranchName());
                evidence.setGitCommitHash(commitInfo.getCommitHash());
                evidence.setGitCommitMessage(commitInfo.getMessage());
                evidence.setGitAuthorName(commitInfo.getAuthorName());
                evidence.setGitAuthorEmail(commitInfo.getAuthorEmail());
                evidence.setGitCommitTime(commitInfo.getCommitTime());
                evidence.setGitRepositoryPath(repository.getRepositoryPath());
                evidence.setGitStatus(1); // 1: 本地成功
                evidence.setChainStatus(Constants.CHAIN_STATUS_PENDING); // 设置为待上链状态
                
                evidence.setCreatedAt(LocalDateTime.now());
                evidence.setUpdatedAt(LocalDateTime.now());
                
                evidenceService.storeEvidence(evidence);
                evidenceList.add(evidence);
            }
            
            updateBranchInfo(request.getRepositoryId(), request.getBranchName(), commitInfo);
            
            log.info("Batch code committed successfully: {} files in {}/{}/{}", 
                    request.getFiles().length, repository.getGroupName(), repository.getProjectName(), request.getBranchName());
            return evidenceList;
            
        } catch (Exception e) {
            log.error("Failed to commit batch code, error: {}", e.getMessage());
            throw new RuntimeException("Failed to commit batch code: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public GitCommitInfo mergeBranch(Long userId, Long repositoryId, String sourceBranch, String targetBranch) {
        GitRepository repository = gitRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            throw new IllegalArgumentException("Repository not found: " + repositoryId);
        }

        if (!repository.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Access denied to repository: " + repositoryId);
        }

        // 确保仓库已验证
        if (repository.getValidationStatus() == null || repository.getValidationStatus() == GitRepository.ValidationStatus.UNVALIDATED) {
            log.info("Repository not validated yet, validating now: {}/{}", repository.getGroupName(), repository.getProjectName());
            validateRepository(repositoryId);
            // 重新查询更新后的仓库信息
            repository = gitRepositoryMapper.selectById(repositoryId);
        }
        
        if (repository.getValidationStatus() == GitRepository.ValidationStatus.VALIDATION_FAILED) {
            throw new IllegalStateException("Repository validation failed, cannot merge branches");
        }

        try {
            GitCommitInfo mergeCommitInfo = nativeGitUtil.mergeBranch(
                repository.getRepositoryPath(),
                sourceBranch,
                targetBranch,
                "User-" + userId,
                "user" + userId + "@evidence.local"
            );
            
            // 更新目标分支信息
            updateBranchInfo(repositoryId, targetBranch, mergeCommitInfo);
            
            log.info("Branch merge completed successfully: {} -> {} in repository {}/{}", 
                    sourceBranch, targetBranch, repository.getGroupName(), repository.getProjectName());
            
            return mergeCommitInfo;
            
        } catch (Exception e) {
            log.error("Failed to merge branch: {} -> {} in repository {}, error: {}", 
                     sourceBranch, targetBranch, repositoryId, e.getMessage());
            throw new RuntimeException("Failed to merge branch: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean pushToRemote(Long repositoryId, String remoteUrl) {
        GitRepository repository = gitRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            throw new IllegalArgumentException("Repository not found: " + repositoryId);
        }

        try {
            boolean success = nativeGitUtil.pushToRemote(
                repository.getRepositoryPath(), 
                remoteUrl, 
                repository.getDefaultBranch()
            );
            
            if (success) {
                repository.setRemoteUrl(remoteUrl);
                repository.setUpdatedAt(LocalDateTime.now());
                gitRepositoryMapper.updateById(repository);
                
                updateEvidenceRemoteStatus(repositoryId, true);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Failed to push repository {} to remote {}, error: {}", repositoryId, remoteUrl, e.getMessage());
            updateEvidenceRemoteStatus(repositoryId, false);
            return false;
        }
    }

    @Override
    public List<GitRepository> getUserRepositories(Long userId) {
        return gitRepositoryMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitRepository>()
                .eq(GitRepository::getUserId, userId)
                .eq(GitRepository::getStatus, 1)
                .orderByDesc(GitRepository::getUpdatedAt)
        );
    }

    @Override
    public List<GitBranch> getRepositoryBranches(Long repositoryId) {
        return gitBranchMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitBranch>()
                .eq(GitBranch::getRepositoryId, repositoryId)
                .orderByDesc(GitBranch::getUpdatedAt)
        );
    }

    @Override
    public List<FileEvidence> getCodeEvidence(Long userId, String groupName, String projectName, String branchName) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileEvidence> wrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileEvidence>()
                .eq(FileEvidence::getUserId, userId)
                .eq(FileEvidence::getContentType, "CODE");
        
        if (groupName != null) {
            wrapper.eq(FileEvidence::getGitGroupName, groupName);
        }
        if (projectName != null) {
            wrapper.eq(FileEvidence::getGitProjectName, projectName);
        }
        if (branchName != null) {
            wrapper.eq(FileEvidence::getGitBranchName, branchName);
        }
        
        return evidenceMapper.selectList(wrapper.orderByDesc(FileEvidence::getCreatedAt));
    }

    @Override
    public GitCommitInfo getLatestCommit(Long repositoryId, String branchName) {
        GitRepository repository = gitRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            throw new IllegalArgumentException("Repository not found: " + repositoryId);
        }

        try {
            return nativeGitUtil.getLatestCommit(repository.getRepositoryPath(), branchName);
        } catch (Exception e) {
            log.error("Failed to get latest commit for repository {}, branch {}, error: {}", 
                     repositoryId, branchName, e.getMessage());
            throw new RuntimeException("Failed to get latest commit: " + e.getMessage(), e);
        }
    }

    @Override
    public FileEvidence getCodeByCommit(String commitHash, String fileName) {
        FileEvidence evidence = evidenceMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileEvidence>()
                .eq(FileEvidence::getGitCommitHash, commitHash)
                .eq(FileEvidence::getFileName, fileName)
                .eq(FileEvidence::getContentType, "CODE")
        );
        
        if (evidence != null) {
            try {
                GitFileInfo fileInfo = nativeGitUtil.getFileFromCommit(
                    evidence.getGitRepositoryPath(), commitHash, fileName);
                
                if (fileInfo != null) {
                    evidence.setDescription(fileInfo.getContent());
                }
            } catch (Exception e) {
                log.warn("Failed to load file content from commit: {}", e.getMessage());
            }
        }
        
        return evidence;
    }

    @Override
    @Transactional
    public void deleteRepository(Long repositoryId) {
        GitRepository repository = gitRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            return;
        }

        try {
            gitBranchMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitBranch>()
                    .eq(GitBranch::getRepositoryId, repositoryId)
            );
            
            evidenceMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileEvidence>()
                    .eq(FileEvidence::getGitRepositoryPath, repository.getRepositoryPath())
            );
            
            gitRepositoryMapper.deleteById(repositoryId);
            
            java.nio.file.Path repoPath = java.nio.file.Paths.get(repository.getRepositoryPath());
            if (java.nio.file.Files.exists(repoPath)) {
                org.apache.commons.io.FileUtils.deleteDirectory(repoPath.toFile());
            }
            
            log.info("Repository deleted successfully: {}", repositoryId);
            
        } catch (Exception e) {
            log.error("Failed to delete repository {}, error: {}", repositoryId, e.getMessage());
            throw new RuntimeException("Failed to delete repository: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteBranch(Long branchId) {
        GitBranch branch = gitBranchMapper.selectById(branchId);
        if (branch == null) {
            return;
        }

        if (branch.getIsProtected()) {
            throw new IllegalArgumentException("Cannot delete protected branch: " + branch.getBranchName());
        }

        GitRepository repository = gitRepositoryMapper.selectById(branch.getRepositoryId());
        if (repository != null && branch.getBranchName().equals(repository.getDefaultBranch())) {
            throw new IllegalArgumentException("Cannot delete default branch: " + branch.getBranchName());
        }

        gitBranchMapper.deleteById(branchId);
        log.info("Branch deleted successfully: {}", branchId);
    }

    private GitRepository getOrCreateRepository(Long userId, String groupName, String projectName, String description) {
        GitRepository repository = gitRepositoryMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitRepository>()
                .eq(GitRepository::getUserId, userId)
                .eq(GitRepository::getGroupName, groupName)
                .eq(GitRepository::getProjectName, projectName)
        );
        
        if (repository == null) {
            repository = createRepository(userId, groupName, projectName, description);
        }
        
        return repository;
    }

    private void updateBranchInfo(Long repositoryId, String branchName, GitCommitInfo commitInfo) {
        GitBranch branch = gitBranchMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitBranch>()
                .eq(GitBranch::getRepositoryId, repositoryId)
                .eq(GitBranch::getBranchName, branchName)
        );
        
        if (branch != null) {
            branch.setLastCommitHash(commitInfo.getCommitHash());
            branch.setLastCommitMessage(commitInfo.getMessage());
            branch.setLastCommitTime(commitInfo.getCommitTime());
            branch.setUpdatedAt(LocalDateTime.now());
            gitBranchMapper.updateById(branch);
        }
    }

    private void updateEvidenceRemoteStatus(Long repositoryId, boolean success) {
        GitRepository repository = gitRepositoryMapper.selectById(repositoryId);
        if (repository == null) {
            return;
        }

        int newStatus = success ? 2 : 3; // 2: 远程成功, 3: 失败
        
        evidenceMapper.update(null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FileEvidence>()
                .eq(FileEvidence::getGitRepositoryPath, repository.getRepositoryPath())
                .set(FileEvidence::getGitStatus, newStatus)
                .set(FileEvidence::getUpdatedAt, LocalDateTime.now())
        );
    }
    
    private void validateRepositoryState(String repositoryPath, String branchName) {
        try {
            log.debug("Validating repository state for path: {} and branch: {}", repositoryPath, branchName);
            
            // Check if repository directory exists
            java.nio.file.Path repoPath = java.nio.file.Paths.get(repositoryPath);
            if (!java.nio.file.Files.exists(repoPath)) {
                throw new IllegalStateException("Repository directory does not exist: " + repositoryPath);
            }
            
            // Check if .git directory exists
            java.nio.file.Path gitPath = repoPath.resolve(".git");
            if (!java.nio.file.Files.exists(gitPath)) {
                throw new IllegalStateException("Git directory does not exist: " + gitPath);
            }
            
            // Additional validation can be added here
            log.debug("Repository state validation successful");
            
        } catch (Exception e) {
            log.error("Repository state validation failed: {}", e.getMessage());
            throw new RuntimeException("Repository state validation failed", e);
        }
    }
    
    private void cleanupFailedRepository(String groupName, String projectName) {
        try {
            log.info("Cleaning up failed repository: {}/{}", groupName, projectName);
            
            // Clean up database records if they exist
            GitRepository repository = gitRepositoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitRepository>()
                    .eq(GitRepository::getGroupName, groupName)
                    .eq(GitRepository::getProjectName, projectName)
            );
            
            if (repository != null) {
                // Delete branch records
                gitBranchMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GitBranch>()
                        .eq(GitBranch::getRepositoryId, repository.getId())
                );
                
                // Delete repository record
                gitRepositoryMapper.deleteById(repository.getId());
                log.info("Cleaned up database records for repository: {}/{}", groupName, projectName);
                
                // Clean up file system
                try {
                    java.nio.file.Path repoPath = java.nio.file.Paths.get(repository.getRepositoryPath());
                    if (java.nio.file.Files.exists(repoPath)) {
                        org.apache.commons.io.FileUtils.deleteDirectory(repoPath.toFile());
                        log.info("Cleaned up repository directory: {}", repository.getRepositoryPath());
                    }
                } catch (Exception e) {
                    log.warn("Failed to clean up repository directory: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to clean up failed repository {}/{}: {}", groupName, projectName, e.getMessage());
        }
    }
    
    private void cleanupOrphanedGitRepository(String groupName, String projectName) {
        try {
            log.info("Cleaning up orphaned Git repository: {}/{}", groupName, projectName);
            
            // Build the expected repository path
            String expectedPath = java.nio.file.Paths.get(
                nativeGitUtil.getGitBasePath(), groupName, projectName
            ).toString();
            
            // Clean up the file system directory
            java.nio.file.Path repoPath = java.nio.file.Paths.get(expectedPath);
            if (java.nio.file.Files.exists(repoPath)) {
                org.apache.commons.io.FileUtils.deleteDirectory(repoPath.toFile());
                log.info("Successfully cleaned up orphaned repository directory: {}", expectedPath);
            } else {
                log.warn("Orphaned repository directory not found: {}", expectedPath);
            }
            
        } catch (Exception e) {
            log.error("Failed to clean up orphaned Git repository {}/{}: {}", groupName, projectName, e.getMessage());
            throw new RuntimeException("Failed to clean up orphaned repository directory", e);
        }
    }
}