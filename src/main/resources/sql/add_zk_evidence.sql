-- ZK 存证扩展：为 file_evidence 表添加承诺相关字段
ALTER TABLE file_evidence
    ADD COLUMN commitment_hash VARCHAR(64)  COMMENT 'SHA-256(file_hash || salt)，上链的承诺值',
    ADD COLUMN salt_hex        VARCHAR(64)  COMMENT '32字节随机盐（十六进制），仅所有者可见',
    ADD COLUMN zk_status       TINYINT DEFAULT 0 COMMENT 'ZK状态：0-未生成，1-已生成，2-失败';

-- ZK 证明记录表
CREATE TABLE IF NOT EXISTS evidence_zk_proofs (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    evidence_id     BIGINT       NOT NULL COMMENT '关联 file_evidence.id',
    user_id         BIGINT       NOT NULL,
    image_id        VARCHAR(128) NOT NULL COMMENT 'RISC Zero ELF 镜像ID',
    commitment_hex  VARCHAR(64)  NOT NULL COMMENT '与链上一致的承诺哈希',
    journal_hex     TEXT         NOT NULL COMMENT '电路 journal 字节（hex）',
    journal_digest  VARCHAR(64)  NOT NULL COMMENT 'SHA-256(journal bytes)',
    seal_hex        MEDIUMTEXT   NOT NULL COMMENT 'Groth16 proof 字节（hex）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK 或 REAL',
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (evidence_id) REFERENCES file_evidence(id) ON DELETE CASCADE,
    INDEX idx_evidence_id (evidence_id),
    INDEX idx_user_id     (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存证ZK证明表';
