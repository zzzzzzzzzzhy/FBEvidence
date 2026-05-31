-- 为file_evidence表添加did_document字段的迁移脚本

USE blockchain_evidence;

-- 检查did_document字段是否已存在，如果不存在则添加
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_SCHEMA = 'blockchain_evidence'
                   AND TABLE_NAME = 'file_evidence'
                   AND COLUMN_NAME = 'did_document');

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE file_evidence ADD COLUMN did_document VARCHAR(128) COMMENT "文件DID标识文档" AFTER chain_message',
    'SELECT "DID document字段已存在，跳过添加" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有文件生成DID document（基于文件哈希）
UPDATE file_evidence
SET did_document = CONCAT('did:evidence:file:', file_hash)
WHERE did_document IS NULL AND file_hash IS NOT NULL;

-- 验证迁移结果
SELECT 'DID document字段添加完成' AS status, COUNT(*) AS total_files,
       COUNT(did_document) AS files_with_did FROM file_evidence;