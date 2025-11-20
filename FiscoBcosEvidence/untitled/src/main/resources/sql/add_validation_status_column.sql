-- 为git_repositories表添加验证状态字段
ALTER TABLE git_repositories 
ADD COLUMN validation_status INT DEFAULT 0 COMMENT '验证状态：0-未验证, 1-已验证, 2-验证失败';

-- 更新现有记录为已验证状态（假设现有记录都是有效的）
UPDATE git_repositories SET validation_status = 1 WHERE validation_status IS NULL;