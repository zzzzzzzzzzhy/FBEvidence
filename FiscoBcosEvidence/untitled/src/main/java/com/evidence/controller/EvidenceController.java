package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.dto.QueryRequest;
import com.evidence.dto.UploadRequest;
import com.evidence.service.EvidenceService;
import com.evidence.vo.EvidenceResponse;
import com.evidence.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.validation.BindingResult;

@Slf4j
@RestController
@RequestMapping("/api/evidence")
@RequiredArgsConstructor
public class EvidenceController {

    private final EvidenceService evidenceService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<EvidenceResponse> uploadEvidence(@Validated @ModelAttribute UploadRequest uploadRequest,
                                                   BindingResult bindingResult) {
        log.info("=== Controller: 文件上传接口接收到请求 ===");
        log.info("文件信息: name={}, size={}, contentType={}, hashAlgorithm={}, description={}", 
                uploadRequest.getFile() != null ? uploadRequest.getFile().getOriginalFilename() : "null",
                uploadRequest.getFile() != null ? uploadRequest.getFile().getSize() : 0,
                uploadRequest.getContentType(),
                uploadRequest.getHashAlgorithm(),
                uploadRequest.getDescription());
        
        try {
            if (bindingResult.hasErrors()) {
                log.error("参数验证失败: {}", bindingResult.getFieldError().getDefaultMessage());
                return Result.error(bindingResult.getFieldError().getDefaultMessage());
            }
            
            log.info("Controller: 调用EvidenceService.uploadEvidence()");
            EvidenceResponse response = evidenceService.uploadEvidence(uploadRequest);
            log.info("Controller: 文件上传成功, 返回响应: {}", response);
            return Result.success("文件上传成功", response);
        } catch (Exception e) {
            log.error("Controller: 文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<PageResponse<EvidenceResponse>> queryEvidence(QueryRequest queryRequest) {
        log.info("=== Controller: /api/evidence/list接口接收到请求 ===");
        try {
            PageResponse<EvidenceResponse> response = evidenceService.queryEvidence(queryRequest);
            return Result.success(response);
        } catch (Exception e) {
            log.error("查询存证失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/query")
    public Result<PageResponse<EvidenceResponse>> queryEvidenceByQuery(QueryRequest queryRequest) {
        log.info("=== Controller: /api/evidence/query接口接收到请求 ===");
        try {
            PageResponse<EvidenceResponse> response = evidenceService.queryEvidence(queryRequest);
            return Result.success(response);
        } catch (Exception e) {
            log.error("查询存证失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<EvidenceResponse> getEvidenceById(@PathVariable Long id) {
        log.info("=== Controller: /api/evidence/{id}接口接收到请求 ===");
        try {
            EvidenceResponse response = evidenceService.getEvidenceById(id);
            return Result.success(response);
        } catch (Exception e) {
            log.error("获取存证详情失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/hash/{hash}")
    public Result<EvidenceResponse> getEvidenceByHash(@PathVariable String hash) {
        log.info("=== Controller: /api/evidence/hash/{hash}接口接收到请求，hash={} ===", hash);
        try {
            // 去除前后空格
            String trimmedHash = hash != null ? hash.trim() : "";
            log.info("处理后的hash: {}", trimmedHash);
            EvidenceResponse response = evidenceService.getEvidenceByHash(trimmedHash);
            return Result.success(response);
        } catch (Exception e) {
            log.error("根据哈希查询存证失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/transaction/{transactionHash}")
    public Result<EvidenceResponse> getEvidenceByTransactionHash(@PathVariable String transactionHash) {
        log.info("=== Controller: /api/evidence/transaction/{transactionHash}接口接收到请求，transactionHash={} ===", transactionHash);
        try {
            // 去除前后空格
            String trimmedHash = transactionHash != null ? transactionHash.trim() : "";
            log.info("处理后的transactionHash: {}", trimmedHash);
            EvidenceResponse response = evidenceService.getEvidenceByTransactionHash(trimmedHash);
            return Result.success(response);
        } catch (Exception e) {
            log.error("根据交易哈希查询存证失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/block/{blockNumber}")
    public Result<java.util.List<EvidenceResponse>> getEvidenceByBlockNumber(@PathVariable Long blockNumber) {
        log.info("=== Controller: /api/evidence/block/{blockNumber}接口接收到请求，blockNumber={} ===", blockNumber);
        try {
            java.util.List<EvidenceResponse> response = evidenceService.getEvidenceByBlockNumber(blockNumber);
            return Result.success(response);
        } catch (Exception e) {
            log.error("根据区块号查询存证失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/verify")
    public Result<Boolean> verifyEvidence(@RequestParam String fileHash) {
        log.info("=== Controller: /api/evidence/verify接口接收到请求，fileHash={} ===", fileHash);
        try {
            // 去除前后空格
            String trimmedHash = fileHash != null ? fileHash.trim() : "";
            log.info("处理后的fileHash: {}", trimmedHash);
            boolean result = evidenceService.verifyEvidence(trimmedHash);
            return Result.success("验证完成", result);
        } catch (Exception e) {
            log.error("验证存证失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public Result<Object> getStatistics() {
        log.info("=== Controller: /api/evidence/hash/statistics接口接收到请求 ===");
        try {
            java.util.Map<String, Object> statistics = evidenceService.getStatistics();
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/test-minio")
    public Result<String> testMinIOConnection() {
        log.info("=== Controller: /api/evidence/hash/test-minio接口接收到请求 ===");
        try {
            log.info("开始测试MinIO连接");
            evidenceService.testMinIOConnection();
            return Result.success("MinIO连接正常");
        } catch (Exception e) {
            log.error("MinIO连接测试失败", e);
            return Result.error("MinIO连接失败: " + e.getMessage());
        }
    }
}