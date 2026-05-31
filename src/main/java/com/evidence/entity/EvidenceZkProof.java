package com.evidence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evidence_zk_proofs")
public class EvidenceZkProof {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long   evidenceId;
    private Long   userId;
    private String imageId;
    private String commitmentHex;
    private String journalHex;
    private String journalDigest;
    private String sealHex;
    private String status;

    private LocalDateTime createdAt;
}
