package com.evidence.service;

import com.evidence.entity.EvidenceZkProof;
import com.evidence.entity.FileEvidence;

import java.util.Map;

public interface ZkEvidenceService {

    /** 为已上传的文件存证生成 ZK 承诺（commitment + salt），写回 file_evidence 表 */
    FileEvidence commitEvidence(Long evidenceId) throws Exception;

    /** 生成 ZK proof（mock 或真实），写入 evidence_zk_proofs */
    EvidenceZkProof generateProof(Long evidenceId) throws Exception;

    /** 获取最新 ZK proof */
    EvidenceZkProof getProof(Long evidenceId);

    /** 公开信息：commitment、zkStatus（不含 salt/file_hash） */
    Map<String, Object> getPublicCommitment(Long evidenceId);
}
