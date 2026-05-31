-- 文件存证可视化管理表结构
-- 分组表
CREATE TABLE file_evidence_groups (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分组ID',
    group_name VARCHAR(100) NOT NULL COMMENT '分组名称',
    group_code VARCHAR(50) UNIQUE NOT NULL COMMENT '分组代码',
    description TEXT COMMENT '分组描述',
    creator_id BIGINT NOT NULL COMMENT '创建者ID',
    creator_name VARCHAR(50) NOT NULL COMMENT '创建者姓名',
    status TINYINT DEFAULT 1 COMMENT '状态: 1=启用, 0=禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_creator_id (creator_id),
    INDEX idx_group_code (group_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存证分组表';

-- 项目表
CREATE TABLE file_evidence_projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '项目ID',
    group_id BIGINT NOT NULL COMMENT '所属分组ID',
    project_name VARCHAR(100) NOT NULL COMMENT '项目名称',
    project_code VARCHAR(50) NOT NULL COMMENT '项目代码',
    description TEXT COMMENT '项目描述',
    creator_id BIGINT NOT NULL COMMENT '创建者ID',
    creator_name VARCHAR(50) NOT NULL COMMENT '创建者姓名',
    status TINYINT DEFAULT 1 COMMENT '状态: 1=启用, 0=禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (group_id) REFERENCES file_evidence_groups(id),
    UNIQUE KEY uk_group_project (group_id, project_code),
    INDEX idx_group_id (group_id),
    INDEX idx_creator_id (creator_id),
    INDEX idx_project_code (project_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存证项目表';

-- 操作日志表
CREATE TABLE file_evidence_operation_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型',
    target_type VARCHAR(20) NOT NULL COMMENT '目标类型: GROUP, PROJECT, FILE',
    target_id BIGINT NOT NULL COMMENT '目标ID',
    target_name VARCHAR(200) NOT NULL COMMENT '目标名称',
    operator_id BIGINT NOT NULL COMMENT '操作者ID',
    operator_name VARCHAR(50) NOT NULL COMMENT '操作者姓名',
    operation_details TEXT COMMENT '操作详情',
    operation_result TINYINT DEFAULT 1 COMMENT '操作结果: 1=成功, 0=失败',
    client_ip VARCHAR(45) COMMENT '客户端IP',
    user_agent TEXT COMMENT '用户代理',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_operator_id (operator_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_target_type (target_type),
    INDEX idx_target_id (target_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存证操作日志表';

-- 修改文件存证表，增加项目关联字段
ALTER TABLE file_evidence 
ADD COLUMN project_id BIGINT NULL COMMENT '所属项目ID' AFTER user_id,
ADD COLUMN commit_message TEXT NULL COMMENT '提交信息' AFTER description,
ADD INDEX idx_project_id (project_id);

-- 添加外键约束（如果需要严格约束的话）
-- ALTER TABLE file_evidence 
-- ADD CONSTRAINT fk_evidence_project FOREIGN KEY (project_id) REFERENCES file_evidence_projects(id);

-- 插入默认分组和项目（可选）
INSERT INTO file_evidence_groups (group_name, group_code, description, creator_id, creator_name) 
VALUES ('默认分组', 'DEFAULT', '系统默认分组，用于未分类文件', 1, 'system');

INSERT INTO file_evidence_projects (group_id, project_name, project_code, description, creator_id, creator_name) 
VALUES (1, '默认项目', 'DEFAULT', '系统默认项目，用于未分类文件', 1, 'system');