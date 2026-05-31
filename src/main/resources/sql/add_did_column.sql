-- 为users表添加did字段的迁移脚本

USE blockchain_evidence;

-- 检查did字段是否已存在，如果不存在则添加
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
                   WHERE TABLE_SCHEMA = 'blockchain_evidence' 
                   AND TABLE_NAME = 'users' 
                   AND COLUMN_NAME = 'did');

SET @sql = IF(@col_exists = 0, 
    'ALTER TABLE users ADD COLUMN did VARCHAR(32) UNIQUE COMMENT "用户DID标识" AFTER email',
    'SELECT "DID字段已存在，跳过添加" AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为现有用户生成DID（如果表中有数据的话）
-- 注意：这里使用简单的哈希方式，实际生产环境中应该使用更安全的方式
UPDATE users 
SET did = CONCAT('did:evidence:', 
    LOWER(LEFT(MD5(CONCAT(username, email, UNIX_TIMESTAMP())), 16))
) 
WHERE did IS NULL;

-- 验证迁移结果
SELECT 'DID字段添加完成' AS status, COUNT(*) AS total_users, 
       COUNT(did) AS users_with_did FROM users;