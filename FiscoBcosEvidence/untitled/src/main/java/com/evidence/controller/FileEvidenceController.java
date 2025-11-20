package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.dto.FileEvidenceBatchUploadRequest;
import com.evidence.dto.FileEvidenceUploadRequest;
import com.evidence.dto.QueryRequest;
import com.evidence.service.EvidenceService;
import com.evidence.service.FileEvidenceManagementService;
import com.evidence.vo.EvidenceResponse;
import com.evidence.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/file-evidence")
@RequiredArgsConstructor
@Validated
public class FileEvidenceController {

    private final EvidenceService evidenceService;
    private final FileEvidenceManagementService managementService;

    @PostMapping("/upload")
    public Result<EvidenceResponse> uploadFileEvidence(
            @Valid @ModelAttribute FileEvidenceUploadRequest uploadRequest,
            HttpServletRequest request) {
        try {
            EvidenceResponse response = evidenceService.uploadFileEvidence(uploadRequest);
            return Result.success(response);
        } catch (Exception e) {
            log.error("文件存证上传失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/upload-batch")
    public Result<List<EvidenceResponse>> uploadFileEvidenceBatch(
            @Valid @ModelAttribute FileEvidenceBatchUploadRequest uploadRequest,
            HttpServletRequest request) {
        try {
            List<EvidenceResponse> responses = evidenceService.uploadFileEvidenceBatch(uploadRequest);
            return Result.success(responses);
        } catch (Exception e) {
            log.error("批量文件存证上传失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/query")
    public Result<PageResponse<EvidenceResponse>> queryFileEvidence(
            @Valid QueryRequest queryRequest,
            @RequestParam(required = false) Long projectId) {
        try {
            PageResponse<EvidenceResponse> response = evidenceService.queryFileEvidence(queryRequest, projectId);
            return Result.success(response);
        } catch (Exception e) {
            log.error("查询文件存证失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<EvidenceResponse> getFileEvidenceById(@PathVariable @NotNull Long id) {
        try {
            EvidenceResponse response = evidenceService.getEvidenceById(id);
            return Result.success(response);
        } catch (Exception e) {
            log.error("获取文件存证详情失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/hash/{fileHash}")
    public Result<EvidenceResponse> getFileEvidenceByHash(@PathVariable String fileHash) {
        try {
            EvidenceResponse response = evidenceService.getEvidenceByHash(fileHash);
            return Result.success(response);
        } catch (Exception e) {
            log.error("根据哈希获取文件存证失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/verify/{fileHash}")
    public Result<Boolean> verifyFileEvidence(@PathVariable String fileHash) {
        try {
            boolean verified = evidenceService.verifyEvidence(fileHash);
            return Result.success(verified);
        } catch (Exception e) {
            log.error("验证文件存证失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getFileEvidenceStats() {
        try {
            Map<String, Object> stats = evidenceService.getFileEvidenceStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取文件存证统计失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}