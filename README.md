# FBEvidence — 区块链代码与文件存证系统

基于 **FISCO BCOS** 联盟链的存证平台，支持文件存证、代码提交自动存证（GitHub Webhook）、零知识证明隐私保护，以及完整的区块链查询与验证。

**在线演示**: `http://107.172.134.19:8080`  
默认账号：`admin / Zhy@2026`

---

## 功能特性

| 模块 | 功能 |
|------|------|
| **文件存证** | 上传文件自动计算 SHA256，存入 FISCO BCOS，支持分组/项目管理 |
| **代码存证** | 本地 Git 仓库管理（创建仓库/分支/提交/合并），文件内容哈希上链 |
| **GitHub Webhook** | push 事件自动触发，commit SHA 实时存入区块链，无需手动操作 |
| **零知识证明** | 为存证生成 ZK 承诺哈希（隐藏原文），支持 Mock/真实两种模式 |
| **存证验证** | 一键验证文件有效性，校验链上交易哈希和区块高度 |
| **区块链浏览器** | 查看区块信息、交易详情、节点状态、合约信息 |
| **用户系统** | JWT 认证，DID 身份标识，注册/登录/权限管理 |
| **国密支持** | SM2/SM3/SM4 全链路国密算法 |

---

## 技术栈

**后端**
- Java 11 + Spring Boot 2.7
- MyBatis Plus + MySQL 8.0
- FISCO BCOS Java SDK 2.9.1（国密模式）
- Redis（缓存 + 异步队列）
- MinIO（文件对象存储）
- JGit（本地 Git 操作）
- JWT 身份认证

**前端**
- 前后端不分离，静态 HTML + Vue.js 2.x + Axios
- 页面：首页/登录、文件存证、代码存证、查询验证、区块链浏览器

**区块链**
- FISCO BCOS 2.x，4 节点联盟链，国密模式
- Solidity 0.4.25 智能合约（`EvidenceContract`）
- 已部署合约地址：`0xa0f88385434996c25d432464ffca1cedabaab5e0`

**ZK 证明**
- RISC Zero zkVM（Rust）
- 支持 Mock 模式（本地验证）和真实 ZK 模式（需编译 `zk/` 目录）

---

## 快速开始

### 环境依赖

- JDK 11+
- Maven 3.6+
- MySQL 8.0（端口 3306）
- Redis
- MinIO
- FISCO BCOS 节点（国密模式，端口 20200-20203）

### 部署步骤

**1. 克隆项目**
```bash
git clone https://github.com/zzzzzzzzzzhy/FBEvidence.git
cd FBEvidence
```

**2. 配置数据库**

创建数据库并执行建表：
```sql
CREATE DATABASE blockchain_evidence DEFAULT CHARACTER SET utf8mb4;
```

修改 `src/main/resources/application-dev.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/blockchain_evidence
    username: your_username
    password: your_password
```

**3. 配置 FISCO BCOS 证书**

将节点 SDK 证书复制到项目：
```bash
cp /path/to/fisco/nodes/127.0.0.1/sdk/ca.crt      src/main/resources/conf/
cp /path/to/fisco/nodes/127.0.0.1/sdk/sdk.crt     src/main/resources/conf/
cp /path/to/fisco/nodes/127.0.0.1/sdk/sdk.key     src/main/resources/conf/
cp /path/to/fisco/nodes/127.0.0.1/sdk/gm/*.crt    src/main/resources/conf/
cp /path/to/fisco/nodes/127.0.0.1/sdk/gm/*.key    src/main/resources/conf/
```

> **注意**：证书必须与节点匹配，不可使用其他节点的证书。

**4. 配置 FISCO BCOS 节点地址**

修改 `src/main/resources/config.toml`：
```toml
[network]
peers = ["127.0.0.1:20200", "127.0.0.1:20201", "127.0.0.1:20202", "127.0.0.1:20203"]
```

**5. 编译启动**
```bash
mvn clean package -DskipTests
nohup java -Dfile.encoding=UTF-8 -Dspring.profiles.active=dev \
  -jar target/blockchain-evidence-system-1.0.0.jar > app.log 2>&1 &
```

访问 `http://localhost:8080`

---

## GitHub Webhook 配置

每次向仓库 push 代码，commit SHA 自动存入 FISCO BCOS：

1. 在 GitHub 仓库 Settings → Webhooks → Add webhook
2. 填写：
   ```
   Payload URL:  http://your-server:8080/webhook/github
   Content type: application/json
   Events:       Just the push event
   ```
3. push 后在**代码存证**页面可看到新增的区块链记录

---

## 零知识证明

存证详情弹窗中提供两步 ZK 操作：

1. **生成ZK承诺**：为文件生成 `commitment_hash`（隐藏原始文件哈希，用于链上隐私存证）
2. **生成ZK证明**：生成可验证的 ZK proof，任何人可验证持有者知晓原文件而无需披露内容

默认使用 **Mock 模式**（Java 内验证，无需额外依赖）。  
真实 ZK 证明需编译 `zk/` 目录下的 Rust 项目（基于 RISC Zero），并配置环境变量 `ZK_EVIDENCE_PROVER_BINARY`。

---

## 数据库表结构

| 表名 | 说明 |
|------|------|
| `users` | 用户信息，含 DID 标识 |
| `file_evidence` | 存证主表（文件/代码均用此表） |
| `file_evidence_groups` | 存证分组 |
| `file_evidence_projects` | 存证项目 |
| `file_evidence_operation_logs` | 操作审计日志 |
| `git_repositories` | 本地 Git 仓库记录 |
| `git_branches` | 分支记录 |
| `evidence_zk_proofs` | ZK 证明记录 |

---

## 主要 API

| 接口 | 说明 |
|------|------|
| `POST /api/auth/login` | 登录，返回 JWT token |
| `POST /api/auth/register` | 注册，自动生成 DID |
| `POST /api/evidence/upload` | 文件上传存证 |
| `GET  /api/evidence/list` | 查询存证列表 |
| `GET  /api/file-evidence/verify/{hash}` | 验证存证有效性 |
| `POST /api/code/commit` | 代码文件提交存证 |
| `GET  /api/code/evidence` | 查询代码存证记录 |
| `POST /webhook/github` | GitHub push 事件接收 |
| `POST /api/zk/evidence/{id}/commit` | 生成 ZK 承诺 |
| `POST /api/zk/evidence/{id}/prove` | 生成 ZK 证明 |
| `GET  /api/blockchain/block-number` | 获取当前区块高度 |
| `GET  /api/blockchain/info` | 获取合约和节点信息 |

---

## 常见问题

**FISCO BCOS SDK 连接失败**  
检查证书是否与节点匹配（`src/main/resources/conf/` 下的证书需与 `/path/to/nodes/sdk/` 一致），以及节点端口 20200-20203 是否可访问。

**存证验证失败**  
确认该存证的 `chain_status = 1` 且 `transaction_hash` 不为空，验证逻辑依赖链上交易回执。

**GitHub Webhook 收不到请求**  
确认服务器 8080 端口对外开放，Payload URL 填写正确的公网地址。

---

## 版本历史

**v1.2.0** (2026-05-31)
- GitHub Webhook 自动存证
- ZK 零知识证明模块（前端集成）
- 代码存证记录表重构（来源区分、链上交易展示）
- 修复 FISCO BCOS 证书配置和存证验证逻辑

**v1.1.0**
- 代码存证模块（本地 Git 仓库管理）
- 文件存证分组/项目管理
- MinIO 对象存储集成
- Redis 异步上链队列

**v1.0.0**
- 基础文件存证与验证
- FISCO BCOS 国密链集成
- JWT 用户认证系统

---

## 许可证

MIT License
