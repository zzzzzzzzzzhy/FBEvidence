-- 扩展file_evidence表，添加Git相关字段
ALTER TABLE file_evidence 
ADD COLUMN IF NOT EXISTS content_type VARCHAR(20) DEFAULT 'FILE' COMMENT '内容类型：FILE-普通文件，CODE-代码文件',
ADD COLUMN IF NOT EXISTS git_group_name VARCHAR(100) COMMENT 'Git分组名称',
ADD COLUMN IF NOT EXISTS git_project_name VARCHAR(100) COMMENT 'Git项目名称', 
ADD COLUMN IF NOT EXISTS git_branch_name VARCHAR(100) DEFAULT 'main' COMMENT 'Git分支名称',
ADD COLUMN IF NOT EXISTS git_commit_hash VARCHAR(40) COMMENT 'Git本地提交哈希',
ADD COLUMN IF NOT EXISTS git_remote_commit_hash VARCHAR(40) COMMENT 'Git远程提交哈希',
ADD COLUMN IF NOT EXISTS git_commit_message TEXT COMMENT 'Git提交信息',
ADD COLUMN IF NOT EXISTS git_author_name VARCHAR(100) COMMENT 'Git提交作者',
ADD COLUMN IF NOT EXISTS git_author_email VARCHAR(100) COMMENT 'Git提交作者邮箱',
ADD COLUMN IF NOT EXISTS git_commit_time TIMESTAMP COMMENT 'Git提交时间',
ADD COLUMN IF NOT EXISTS git_repository_path VARCHAR(500) COMMENT 'Git仓库本地路径',
ADD COLUMN IF NOT EXISTS git_remote_url VARCHAR(500) COMMENT 'Git远程仓库地址',
ADD COLUMN IF NOT EXISTS git_status TINYINT DEFAULT 0 COMMENT 'Git状态：0-待提交，1-本地成功，2-远程成功，3-失败';

-- 创建Git仓库配置表
CREATE TABLE IF NOT EXISTS git_repositories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '仓库ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    group_name VARCHAR(100) NOT NULL COMMENT '分组名称',
    project_name VARCHAR(100) NOT NULL COMMENT '项目名称',
    repository_path VARCHAR(500) NOT NULL COMMENT '本地仓库路径',
    remote_url VARCHAR(500) COMMENT '远程仓库地址',
    default_branch VARCHAR(100) DEFAULT 'main' COMMENT '默认分支',
    description TEXT COMMENT '仓库描述',
    status TINYINT DEFAULT 1 COMMENT '状态：1-正常，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_group_project (user_id, group_name, project_name),
    INDEX idx_group_project (group_name, project_name),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Git仓库配置表';

-- 创建Git分支管理表
CREATE TABLE IF NOT EXISTS git_branches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分支ID',
    repository_id BIGINT NOT NULL COMMENT '仓库ID',
    branch_name VARCHAR(100) NOT NULL COMMENT '分支名称',
    base_branch VARCHAR(100) COMMENT '基础分支',
    last_commit_hash VARCHAR(40) COMMENT '最新提交哈希',
    last_commit_message TEXT COMMENT '最新提交信息',
    last_commit_time TIMESTAMP COMMENT '最新提交时间',
    is_protected TINYINT DEFAULT 0 COMMENT '是否受保护：1-是，0-否',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    FOREIGN KEY (repository_id) REFERENCES git_repositories(id) ON DELETE CASCADE,
    UNIQUE KEY uk_repo_branch (repository_id, branch_name),
    INDEX idx_repository_id (repository_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Git分支管理表';

-- 为file_evidence表创建新的索引
ALTER TABLE file_evidence ADD INDEX idx_content_type (content_type);
ALTER TABLE file_evidence ADD INDEX idx_git_group_project (git_group_name, git_project_name);
ALTER TABLE file_evidence ADD INDEX idx_git_commit_hash (git_commit_hash);
ALTER TABLE file_evidence ADD INDEX idx_git_status (git_status);
ALTER TABLE file_evidence ADD INDEX idx_git_commit_time (git_commit_time);