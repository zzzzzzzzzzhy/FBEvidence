package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.entity.EvidenceZkProof;
import com.evidence.entity.FileEvidence;
import com.evidence.service.ZkEvidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ZK 存证承诺接口
 *
 * 链路：
 *   1. POST /zk/evidence/{id}/commit  — 生成承诺（commitment + salt），写回 file_evidence
 *   2. POST /zk/evidence/{id}/prove   — 生成 ZK proof（mock 或真实）
 *   3. GET  /zk/evidence/{id}/proof   — 获取最新 proof
 *   4. GET  /zk/evidence/{id}/commitment — 公开承诺信息（不含 salt）
 */
@Slf4j
@RestController
@RequestMapping("/api/zk/evidence")
@RequiredArgsConstructor
public class ZkEvidenceController {

    private final ZkEvidenceService zkService;

    /**
     * Step 1：为已上传的文件存证生成 ZK 承诺。
     * 调用后 file_evidence.commitment_hash 和 salt_hex 被填写，
     * commitment_hash 可上链（替代原始 file_hash）。
     */
    @PostMapping("/{id}/commit")
    public Result<FileEvidence> commit(@PathVariable Long id) {
        log.info("[ZK] commit evidence {}", id);
        try {
            return Result.success("承诺生成成功", zkService.commitEvidence(id));
        } catch (Exception e) {
            log.error("[ZK] commit failed evidenceId={}", id, e);
            return Result.error("承诺生成失败: " + e.getMessage());
        }
    }

    /**
     * Step 2：生成 ZK proof。
     * 若 ZK_EVIDENCE_PROVER_BINARY 未配置，使用 mock 模式（Java 内验证）。
     * 真实模式需要先编译 zk/ 目录下的 Rust 项目。
     */
    @PostMapping("/{id}/prove")
    public Result<EvidenceZkProof> prove(@PathVariable Long id) {
        log.info("[ZK] prove evidence {}", id);
        try {
            return Result.success("证明生成成功", zkService.generateProof(id));
        } catch (Exception e) {
            log.error("[ZK] prove failed evidenceId={}", id, e);
            return Result.error("证明生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新 ZK proof（公开，任何人可查）。
     */
    @GetMapping("/{id}/proof")
    public Result<EvidenceZkProof> proof(@PathVariable Long id) {
        EvidenceZkProof proof = zkService.getProof(id);
        if (proof == null) return Result.error("该存证尚未生成 ZK proof");
        return Result.success(proof);
    }

    /**
     * 获取公开承诺信息（commitment_hash + zkStatus，不含 salt/file_hash）。
     * 任何人可调用，用于链上对比验证。
     */
    @GetMapping("/{id}/commitment")
    public Result<Map<String, Object>> commitment(@PathVariable Long id) {
        try {
            return Result.success(zkService.getPublicCommitment(id));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
