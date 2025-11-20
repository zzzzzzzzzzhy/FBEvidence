package com.evidence.service;

import com.evidence.dto.BatchCodeUploadRequest;
import com.evidence.dto.CodeUploadRequest;
import com.evidence.entity.FileEvidence;
import com.evidence.entity.GitBranch;
import com.evidence.entity.GitRepository;
import com.evidence.util.NativeGitUtil.GitCommitInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CodeEvidenceService {
    
    GitRepository createRepository(Long userId, String groupName, String projectName, String description);
    
    GitBranch createBranch(Long repositoryId, String branchName, String baseBranch);
    
    FileEvidence uploadCode(Long userId, CodeUploadRequest request);
    
    FileEvidence commitCode(Long userId, Long repositoryId, String branchName, MultipartFile file, 
                           String fileName, String commitMessage);
    
    List<FileEvidence> commitCodeBatch(Long userId, BatchCodeUploadRequest request);
    
    GitCommitInfo mergeBranch(Long userId, Long repositoryId, String sourceBranch, String targetBranch);
    
    boolean pushToRemote(Long repositoryId, String remoteUrl);
    
    List<GitRepository> getUserRepositories(Long userId);
    
    List<GitBranch> getRepositoryBranches(Long repositoryId);
    
    List<FileEvidence> getCodeEvidence(Long userId, String groupName, String projectName, String branchName);
    
    GitCommitInfo getLatestCommit(Long repositoryId, String branchName);
    
    FileEvidence getCodeByCommit(String commitHash, String fileName);
    
    void validateRepository(Long repositoryId);
    
    void deleteRepository(Long repositoryId);
    
    void deleteBranch(Long branchId);
}