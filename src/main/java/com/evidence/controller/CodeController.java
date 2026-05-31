package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.dto.BatchCodeUploadRequest;
import com.evidence.dto.CodeUploadRequest;
import com.evidence.entity.FileEvidence;
import com.evidence.entity.GitBranch;
import com.evidence.entity.GitRepository;
import com.evidence.service.CodeEvidenceService;
import com.evidence.service.UserService;
import com.evidence.entity.User;
import com.evidence.util.JwtUtil;
import com.evidence.util.NativeGitUtil.GitCommitInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
@Validated
public class CodeController {

    private final CodeEvidenceService codeEvidenceService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    
    @Value("${app.auth.skip:false}")
    private boolean skipAuth;

    @PostMapping("/repository")
    public Result<GitRepository> createRepository(
            @RequestParam @NotBlank String groupName,
            @RequestParam @NotBlank String projectName,
            @RequestParam(required = false) String description,
            HttpServletRequest request) {
        
        try {
            Long userId = getUserId(request);
            GitRepository repository = codeEvidenceService.createRepository(userId, groupName, projectName, description);
            return Result.success(repository);
        } catch (Exception e) {
            log.error("Failed to create repository: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/branch")
    public Result<GitBranch> createBranch(
            @RequestParam @NotNull Long repositoryId,
            @RequestParam @NotBlank String branchName,
            @RequestParam(required = false) String baseBranch,
            HttpServletRequest request) {
        
        try {
            getUserId(request);
            GitBranch branch = codeEvidenceService.createBranch(repositoryId, branchName, baseBranch);
            return Result.success(branch);
        } catch (Exception e) {
            log.error("Failed to create branch: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/upload")
    public Result<FileEvidence> uploadCode(
            @Valid @ModelAttribute CodeUploadRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getUserId(httpRequest);
            FileEvidence evidence = codeEvidenceService.uploadCode(userId, request);
            return Result.success(evidence);
        } catch (Exception e) {
            log.error("Failed to upload code: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/commit")
    public Result<FileEvidence> commitCode(
            @RequestParam @NotNull Long repositoryId,
            @RequestParam @NotBlank String branchName,
            @RequestParam @NotNull MultipartFile file,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String commitMessage,
            HttpServletRequest request) {
        
        try {
            Long userId = getUserId(request);
            String actualFileName = fileName != null ? fileName : file.getOriginalFilename();
            String actualCommitMessage = commitMessage != null ? commitMessage : "Upload: " + actualFileName;
            
            FileEvidence evidence = codeEvidenceService.commitCode(
                userId, repositoryId, branchName, file, actualFileName, actualCommitMessage);
            return Result.success(evidence);
        } catch (Exception e) {
            log.error("Failed to commit code: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/commit-batch")
    public Result<List<FileEvidence>> commitCodeBatch(
            @RequestParam @NotNull Long repositoryId,
            @RequestParam @NotBlank String branchName,
            @RequestParam @NotNull MultipartFile[] files,
            @RequestParam @NotBlank String commitMessage,
            HttpServletRequest request) {
        
        try {
            Long userId = getUserId(request);
            
            BatchCodeUploadRequest batchRequest = new BatchCodeUploadRequest();
            batchRequest.setRepositoryId(repositoryId);
            batchRequest.setBranchName(branchName);
            batchRequest.setFiles(files);
            batchRequest.setCommitMessage(commitMessage);
            
            List<FileEvidence> evidenceList = codeEvidenceService.commitCodeBatch(userId, batchRequest);
            return Result.success(evidenceList);
        } catch (Exception e) {
            log.error("Failed to commit batch code: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/merge")
    public Result<GitCommitInfo> mergeBranch(
            @RequestParam @NotNull Long repositoryId,
            @RequestParam @NotBlank String sourceBranch,
            @RequestParam @NotBlank String targetBranch,
            HttpServletRequest request) {
        
        try {
            Long userId = getUserId(request);
            GitCommitInfo mergeCommitInfo = codeEvidenceService.mergeBranch(userId, repositoryId, sourceBranch, targetBranch);
            return Result.success("分支合并成功", mergeCommitInfo);
        } catch (Exception e) {
            log.error("Failed to merge branch: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/push")
    public Result<Boolean> pushToRemote(
            @RequestParam @NotNull Long repositoryId,
            @RequestParam @NotBlank String remoteUrl,
            HttpServletRequest request) {
        
        try {
            getUserId(request);
            boolean success = codeEvidenceService.pushToRemote(repositoryId, remoteUrl);
            if (success) {
                return Result.success("推送成功", true);
            } else {
                return Result.error("推送失败");
            }
        } catch (Exception e) {
            log.error("Failed to push to remote: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/repositories")
    public Result<List<GitRepository>> getUserRepositories(HttpServletRequest request) {
        try {
            Long userId = getUserId(request);
            List<GitRepository> repositories = codeEvidenceService.getUserRepositories(userId);
            return Result.success(repositories);
        } catch (Exception e) {
            log.error("Failed to get user repositories: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/branches")
    public Result<List<GitBranch>> getRepositoryBranches(
            @RequestParam @NotNull Long repositoryId,
            HttpServletRequest request) {
        
        try {
            getUserId(request);
            List<GitBranch> branches = codeEvidenceService.getRepositoryBranches(repositoryId);
            return Result.success(branches);
        } catch (Exception e) {
            log.error("Failed to get repository branches: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/repository/{repositoryId}/validate")
    public Result<String> validateRepository(
            @PathVariable @NotNull Long repositoryId,
            HttpServletRequest request) {
        
        try {
            getUserId(request);
            codeEvidenceService.validateRepository(repositoryId);
            return Result.success("仓库验证成功", null);
        } catch (Exception e) {
            log.error("Failed to validate repository: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/evidence")
    public Result<List<FileEvidence>> getCodeEvidence(
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String branchName,
            HttpServletRequest request) {
        
        try {
            Long userId = getUserId(request);
            List<FileEvidence> evidenceList = codeEvidenceService.getCodeEvidence(userId, groupName, projectName, branchName);
            return Result.success(evidenceList);
        } catch (Exception e) {
            log.error("Failed to get code evidence: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/latest-commit")
    public Result<GitCommitInfo> getLatestCommit(
            @RequestParam @NotNull Long repositoryId,
            @RequestParam(required = false) String branchName,
            HttpServletRequest request) {
        
        try {
            getUserId(request);
            GitCommitInfo commitInfo = codeEvidenceService.getLatestCommit(repositoryId, branchName);
            return Result.success(commitInfo);
        } catch (Exception e) {
            log.error("Failed to get latest commit: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/commit-code")
    public Result<FileEvidence> getCodeByCommit(
            @RequestParam @NotBlank String commitHash,
            @RequestParam @NotBlank String fileName,
            HttpServletRequest request) {
        
        try {
            getUserId(request);
            FileEvidence evidence = codeEvidenceService.getCodeByCommit(commitHash, fileName);
            if (evidence != null) {
                return Result.success(evidence);
            } else {
                return Result.error("代码文件未找到");
            }
        } catch (Exception e) {
            log.error("Failed to get code by commit: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/repository/{repositoryId}")
    public Result<Void> deleteRepository(
            @PathVariable @NotNull Long repositoryId,
            HttpServletRequest request) {
        
        try {
            getUserId(request);
            codeEvidenceService.deleteRepository(repositoryId);
            return Result.success("仓库删除成功", null);
        } catch (Exception e) {
            log.error("Failed to delete repository: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/branch/{branchId}")
    public Result<Void> deleteBranch(
            @PathVariable @NotNull Long branchId,
            HttpServletRequest request) {
        
        try {
            getUserId(request);
            codeEvidenceService.deleteBranch(branchId);
            return Result.success("分支删除成功", null);
        } catch (Exception e) {
            log.error("Failed to delete branch: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    private Long getUserId(HttpServletRequest request) {
        try {
            // 统一使用userService获取当前用户信息，与其他模块保持一致
            User currentUser = userService.getCurrentUser();
            return currentUser.getId();
        } catch (Exception e) {
            // 开发环境跳过认证的后备方案
            if (skipAuth) {
                log.warn("使用开发环境默认用户ID: {}", e.getMessage());
                return 1L; // 使用用户ID 1
            }
            throw new IllegalArgumentException("获取当前用户失败: " + e.getMessage());
        }
    }
}