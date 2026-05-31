-- 创建数据库
CREATE DATABASE IF NOT EXISTS blockchain_evidence DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE blockchain_evidence;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
                                     username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password VARCHAR(128) NOT NULL COMMENT '密码（加密后）',
    real_name VARCHAR(100) COMMENT '真实姓名',
    email VARCHAR(100) COMMENT '邮箱',
    did VARCHAR(32) UNIQUE COMMENT '用户DID标识',
    status TINYINT DEFAULT 1 COMMENT '状态：1启用，0禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 文件存证表
CREATE TABLE IF NOT EXISTS file_evidence (
                                             id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '存证ID',
                                             user_id BIGINT NOT NULL COMMENT '用户ID',
                                             file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_hash VARCHAR(64) UNIQUE NOT NULL COMMENT '文件哈希',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    file_path VARCHAR(500) NOT NULL COMMENT '本地存储路径',
    hash_algorithm VARCHAR(20) DEFAULT 'SHA256' COMMENT '哈希算法',
    description TEXT COMMENT '描述信息',

    -- 区块链相关信息
    transaction_hash VARCHAR(66) COMMENT '交易哈希',
    block_number BIGINT COMMENT '区块号',
    contract_address VARCHAR(42) COMMENT '合约地址',
    chain_status TINYINT DEFAULT 0 COMMENT '链上状态：0待上链，1成功，2失败',
    chain_message TEXT COMMENT '链上返回信息',

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_file_hash (file_hash),
    INDEX idx_user_id (user_id),
    INDEX idx_transaction_hash (transaction_hash),
    INDEX idx_block_number (block_number),
    INDEX idx_chain_status (chain_status),
    INDEX idx_created_at (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件存证表';

-- 插入测试用户
INSERT INTO users (username, password, real_name, email) VALUES
                                                             ('admin', '$2a$10$7JB720yubVSELcNGbO3Ej.5YuvfbVRfYb7I/TbQKBbS9ueOXJx.fq', '系统管理员', 'admin@example.com'),
                                                             ('user1', '$2a$10$7JB720yubVSELcNGbO3Ej.5YuvfbVRfYb7I/TbQKBbS9ueOXJx.fq', '测试用户1', 'user1@example.com');
-- 默认密码都是: 123123