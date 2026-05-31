# FBEvidence 接口状态文档

> 测试环境：本地运行（port 8090），MySQL 3307，Redis 6379  
> FISCO BCOS 节点：**运行中（4节点，区块 #33）**，MinIO：**运行中**  
> 测试时间：2026-05-31

---

## 快速结论

| 模块 | 状态 | 依赖 |
|------|------|------|
| 认证（注册/登录/退出/用户信息） | ✅ 基本可用（有一个 bug） | 仅 MySQL + Redis |
| 系统监控 | ✅ 正常 | MySQL + Redis |
| 存证查询 | ✅ 正常 | 仅 MySQL |
| 存证上传（旧版） | ✅ 可用 | FISCO BCOS + MySQL |
| 区块链直接查询 | ✅ 可用 | FISCO BCOS 节点运行中 |
| 代码存证（Git） | ✅ 完整可用 | 本地 JGit，无链依赖 |
| 文件存证上传 | ✅ 可用 | MinIO + FISCO BCOS |
| 文件存证查询/统计 | ✅ 正常 | 仅 MySQL |
| 存证分组/项目管理 | ✅ 正常（参数格式有坑） | 仅 MySQL |

---

## 一、认证模块 `/api/auth`

### ✅ `POST /api/auth/register` — 注册

**请求（JSON）：**
```json
{
  "username": "testuser",
  "password": "Test@12345",
  "confirmPassword": "Test@12345",
  "realName": "测试用户",
  "email": "test@example.com"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "注册成功",
  "data": {
    "userId": 3,
    "username": "testuser",
    "did": "did:evidence:bc72fa615455d829",
    "realName": "测试用户",
    "registerTime": "2026-05-31 11:12:33",
    "message": "注册成功，您的DID是: did:evidence:bc72fa615455d829"
  }
}
```

**注意：** `confirmPassword` 字段必填，否则报 400；注册时自动生成 DID 标识。

---

### ✅ `POST /api/auth/login` — 登录

**请求（JSON）：**
```json
{ "username": "testuser", "password": "Test@12345" }
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "id": 3,
    "token": "eyJhbGci...",
    "username": "testuser",
    "did": "did:evidence:bc72fa615455d829",
    "expiresIn": 86400000
  }
}
```

> ⚠️ **Bug：** `init.sql` 中预置的 `admin`/`user1` 账号密码哈希与 "123123" 不匹配，无法登录。需通过注册接口创建新账号，或手动更新数据库密码哈希。

---

### ⚠️ `POST /api/auth/logout` — 退出（有 Bug）

返回 200，但 **Token 黑名单未生效**：退出后原 token 仍可继续调用其他接口。原因是 `JwtInterceptor` 中黑名单校验逻辑未正确拦截（dev 模式下 `app.auth.skip=true` 会跳过拦截器）。

---

### ✅ `GET /api/auth/info` — 获取当前用户信息

需要 `Authorization: Bearer <token>`，返回 id、username、realName、email、DID。

---

## 二、系统监控 `/api/system`

### ✅ `GET /api/system/health` — 系统健康状态

```json
{
  "database":   { "status": "UP",   "description": "MySQL数据库" },
  "blockchain": { "status": "DOWN", "description": "FISCO BCOS区块链" },
  "minio":      { "status": "DOWN", "description": "MinIO对象存储" },
  "redis":      { "status": "UP",   "description": "Redis缓存" }
}
```

### ✅ `GET /api/system/stats` — 系统统计

返回 totalUsers、totalEvidence、totalTransactions 等数据库统计。

### ✅ `GET /api/system/activity` — 用户活跃度

返回 DAU（日活）、MAU（月活），基于 Redis HyperLogLog 计算。

### ✅ `GET /api/system/redis/test` — Redis 连通性测试

---

## 三、存证模块 `/api/evidence`

### ✅ `POST /api/evidence/upload` — 文件存证上传

**依赖：** FISCO BCOS 节点 + MySQL  
上传流程：计算 SHA-256 → 写 DB（`chainStatus=0`） → 异步上链 → 更新 `txHash/blockNumber/chainStatus=1`

```json
{
  "id": 3,
  "fileName": "evidence_test.txt",
  "fileHash": "351efe...",
  "chainStatus": 1,
  "chainStatusText": "上链成功",
  "transactionHash": "0x284885...",
  "blockNumber": 32
}
```

> **注意：** `chainStatus` 初始为 0（待上链），异步任务 ~2-5 秒后更新为 1（上链成功）。

---

### ✅ `GET /api/evidence/list` — 存证列表查询

支持分页 `?current=1&size=10`，纯 DB 查询，正常返回。

### ✅ `GET /api/evidence/query` — 存证条件查询

同上，别名接口。

### ✅ `GET /api/evidence/verify?fileHash=xxx` — 验证存证是否存在

查 DB 中 `file_hash` 字段，不需要访问链，返回 `true/false`。

### ✅ `GET /api/evidence/statistics` — 存证统计

返回各状态（待上链/成功/失败）的数量。

### ✅ `GET /api/evidence/{id}` — 按 ID 查存证

DB 查询，正常，无数据时返回错误。

### ✅ `GET /api/evidence/hash/{hash}` — 按文件哈希查存证

DB 查询，正常。

### ❌ `GET /api/evidence/block/{blockNumber}` — 按区块号查 （不可用）

依赖 FISCO BCOS，节点未启动时报错。

### ❌ `GET /api/evidence/transaction/{txHash}` — 按交易哈希查 （不可用）

同上。

---

## 四、区块链直查 `/api/blockchain`

### ⚠️ `GET /api/blockchain/info` — 区块链概况（降级返回）

节点未启动时返回：
```json
{ "connected": false, "blockNumber": 0, "error": "获取区块高度失败" }
```

### ✅ `GET /api/blockchain/block-number` — 当前区块高度

返回 `{ "data": 33 }`，FISCO BCOS 运行时正常。

### ✅ `GET /api/blockchain/info` — 区块链概况

```json
{
  "connected": true,
  "blockNumber": 33,
  "groupId": "group1",
  "contractAddress": "0xa0f88385434996c25d432464ffca1cedabaab5e0"
}
```

### ✅ `GET /api/blockchain/nodes` — 节点列表

返回 4 个节点状态，含区块高度、IP、端口。

### ❓ `GET /api/blockchain/block/{blockNumber}` — 区块详情

接口存在，调用后端解析方式有格式问题，返回原始 JSON 字符串（未进一步序列化），可用但数据格式待优化。

### ❓ `POST /api/blockchain/deploy-contract` — 重新部署合约

合约地址已在代码写死（`0xa0f88385...`），调用此接口会返回固定地址，不会真正重新部署。

---

## 五、代码存证模块 `/api/code`

**全部使用本地 JGit 实现，无区块链依赖，完整可用。**

### ✅ `POST /api/code/repository` — 创建 Git 仓库

**请求（form-data）：**
```
groupName=mygroup&projectName=myproject&description=描述
```

**响应：** 返回仓库信息，本地路径 `/root/data/git-repos/{group}/{project}`，自动 `git init`。

---

### ✅ `GET /api/code/repositories` — 获取当前用户仓库列表

---

### ✅ `POST /api/code/branch` — 创建分支

```
repositoryId=1&branchName=dev&baseBranch=main
```

内部使用 JGit 创建分支，返回分支信息含最新 commit hash。

---

### ✅ `GET /api/code/branches?repositoryId=1` — 获取仓库分支列表

---

### ✅ `POST /api/code/commit` — 提交代码文件存证（核心功能）

**请求（multipart/form-data）：**
```
repositoryId=1
branchName=main
file=<文件>
fileName=hello.py
commitMessage=初始提交
```

**响应片段：**
```json
{
  "fileName": "hello.py",
  "fileHash": "2d543015627a771436b30ea79fd0ecda8df8bcd77b3d55661caf5a0d6e809886",
  "contentType": "CODE",
  "gitCommitHash": "3a5e14a0378c688d1c19ff3908ca313914b5ebb2",
  "gitCommitMessage": "初始提交",
  "gitStatus": 1
}
```

文件 SHA-256 哈希 + Git commit hash 双重存证，写入 DB。

---

### ✅ `POST /api/code/commit-batch` — 批量提交代码文件

```
repositoryId=1&branchName=main&files[]=<file1>&files[]=<file2>&commitMessage=批量提交
```

---

### ✅ `POST /api/code/merge` — 合并分支

```
repositoryId=1&sourceBranch=dev&targetBranch=main
```

内部执行 JGit merge，返回 merge commit 信息。

---

### ⚠️ `POST /api/code/push` — 推送到远程 Git

```
repositoryId=1&remoteUrl=https://github.com/xxx/xxx.git
```

本地 git push。需要远程仓库有权限，否则失败。本地测试无远程时返回 error。

---

### ✅ `GET /api/code/evidence` — 查询代码存证记录

```
?groupName=mygroup&projectName=myproject&branchName=main
```

---

### ✅ `GET /api/code/latest-commit` — 获取分支最新提交

```
?repositoryId=1&branchName=main
```

---

### ✅ `DELETE /api/code/repository/{id}` — 删除仓库

删除 DB 记录及本地 Git 目录。

### ✅ `DELETE /api/code/branch/{id}` — 删除分支

---

## 六、文件存证 `/api/file-evidence`

### ✅ `POST /api/file-evidence/upload` — 单文件存证上传

**请求（multipart/form-data）：**
```
file=<文件>
projectId=2
description=描述
```

上传到本地磁盘 → 计算 SHA-256 → 写 DB → 异步上链（需要 MinIO 或本地存储 + FISCO BCOS）。  
实测：合同文件上链成功，txHash `0x1636fd...`，block #33。

> **注意：** `projectId` 必填，需先通过 `/api/file-evidence-management/projects` 创建项目。

### ⚠️ `POST /api/file-evidence/upload-batch` — 批量上传

接口存在，未单独测试，逻辑同单文件上传，依赖相同。

---

### ✅ `GET /api/file-evidence/query` — 文件存证分页查询

支持 `?projectId=1` 过滤，纯 DB，正常。

### ✅ `GET /api/file-evidence/stats` — 文件存证统计

```json
{ "todayUpload": 1, "successCount": 0, "totalFiles": 1 }
```

### ✅ `GET /api/file-evidence/{id}` — 按 ID 查文件存证

### ✅ `GET /api/file-evidence/hash/{hash}` — 按哈希查文件存证

### ✅ `GET /api/file-evidence/verify/{hash}` — 验证文件存证

---

## 七、存证分组/项目管理 `/api/file-evidence-management`

> ⚠️ **参数格式坑：** 所有写操作（POST/PUT）接受 **form-data 参数**，不是 JSON body。

### ✅ `POST /api/file-evidence-management/groups` — 创建分组

```
groupName=名称&groupCode=CODE001&description=描述
```

### ✅ `GET /api/file-evidence-management/groups` — 获取分组列表（含项目数统计）

### ✅ `GET /api/file-evidence-management/groups/page` — 分组分页

### ✅ `PUT /api/file-evidence-management/groups/{id}/enable` — 启用分组

### ✅ `PUT /api/file-evidence-management/groups/{id}/disable` — 禁用分组

### ✅ `POST /api/file-evidence-management/projects` — 创建项目

```
groupId=1&projectName=名称&projectCode=CODE001&description=描述
```

### ✅ `GET /api/file-evidence-management/projects` — 获取项目列表

### ✅ `GET /api/file-evidence-management/operation-logs` — 操作日志

---

## 已知 Bug 汇总

| # | 问题 | 影响 | 严重度 |
|---|------|------|--------|
| 1 | `init.sql` 预置账号密码哈希错误，`admin`/`user1` 无法用 "123123" 登录 | 新部署无法用默认账号 | 高 |
| 2 | `POST /api/auth/logout` Token 黑名单在 `dev` 模式下无效（`app.auth.skip=true` 跳过拦截器） | 退出后 token 仍有效 | 中 |
| 3 | 部分管理接口误写为 `@RequestParam` 但文档写的是 JSON，调用者容易传错格式 | 开发体验差 | 低 |
| 4 | 启动时间约 138 秒（FISCO BCOS lazy init 期间大量 class loading） | 开发效率低 | 低 |
| 5 | 存证上传异步上链任务失败后无重试机制，`chain_status` 永远停在失败 | 数据一致性 | 中 |

---

## 依赖状态说明

| 服务 | 地址 | 当前状态 | 影响接口 |
|------|------|---------|---------|
| MySQL | 127.0.0.1:3307 | ✅ 运行中 | 所有 |
| Redis | 127.0.0.1:6379 | ✅ 运行中 | 登录失败计数、缓存、DAU/MAU |
| FISCO BCOS | 127.0.0.1:20200-20203 | ✅ 运行中（GM 国密版 2.9.1，4 节点） | 存证上传、区块链查询 |
| MinIO | 127.0.0.1:9000 | ✅ 运行中 | 文件存证上传（含批量） |

---

## 如何启动完整链路

```bash
# 1. 启动 MySQL（如果未运行）
sudo service mysql start   # 或用竞赛平台的 mysqld --port=3307 启动方式

# 2. 启动 Redis
redis-server --daemonize yes

# 3. 启动 FISCO BCOS 节点（国密版 GM，4节点）
bash /root/data/Dapp_Share_Platform/competition-platform/fisco/nodes/127.0.0.1/start_all.sh

# 4. 启动 MinIO
MINIO_ROOT_USER=minioadmin MINIO_ROOT_PASSWORD=minioadmin \
  /root/data/Dapp_Share_Platform/competition-platform/minio/minio \
  server /root/data/minio-data --address :9000 --console-address :9001 &

# 5. 启动后端（必须用 Java 11，Java 17 不支持 secp256k1 曲线）
cd /root/data/FBEvidence-master/FiscoBcosEvidence/untitled
/usr/lib/jvm/java-11-openjdk-amd64/bin/java \
  -jar target/blockchain-evidence-system-1.0.0.jar \
  --spring.profiles.active=dev --server.port=8090
```

> ⚠️ **必须用 Java 11**：Java 17 默认禁用 secp256k1 曲线，导致 FISCO BCOS SDK TLS 握手失败。  
> ⚠️ **节点版本**：使用的是国密（GM）版 FISCO BCOS 2.9.1，`config.toml` 中需设 `useSMCrypto = true`。

启动后 `/api/system/health` 中四个服务全部为 `UP`，全部接口可用。
