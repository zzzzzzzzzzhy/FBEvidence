-- 数据库升级脚本：添加内容类型支持
-- 执行日期：需要在应用部署前执行

USE blockchain_evidence;

-- 1. 添加content_type字段
ALTER TABLE file_evidence 
ADD COLUMN content_type VARCHAR(10) DEFAULT 'FILE' COMMENT '内容类型：FILE-文件上传，TEXT-文字内容' 
AFTER description;

-- 2. 更新现有数据（将现有记录标记为文件类型）
UPDATE file_evidence 
SET content_type = 'FILE' 
WHERE content_type IS NULL OR content_type = '';

-- 3. 添加索引以提高查询性能
ALTER TABLE file_evidence 
ADD INDEX idx_content_type (content_type);

-- 4. 验证更新结果
SELECT 
    content_type, 
    COUNT(*) as count 
FROM file_evidence 
GROUP BY content_type;

-- 查看表结构
DESCRIBE file_evidence;

-- 完成提示
SELECT '数据库升级完成：已添加content_type字段支持' as message;