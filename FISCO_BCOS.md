# FISCO BCOS 模块技术文档

> 基于 FISCO BCOS 2.9.1（国密版）+ Java SDK 2.9.1 + Spring Boot 2.7

---

## 目录

1. [为什么选 FISCO BCOS](#1-为什么选-fisco-bcos)
2. [链的基本架构](#2-链的基本架构)
3. [国密算法](#3-国密算法)
4. [智能合约设计](#4-智能合约设计)
5. [Java SDK 接入](#5-java-sdk-接入)
6. [异步上链队列](#6-异步上链队列)
7. [GitHub Webhook 自动存证](#7-github-webhook-自动存证)
8. [API 接口](#8-api-接口)
9. [证书体系](#9-证书体系)
10. [节点配置与部署](#10-节点配置与部署)
11. [链上数据与数据库对照](#11-链上数据与数据库对照)
12. [常见问题排查](#12-常见问题排查)

---

## 1. 为什么选 FISCO BCOS

### 公链 vs 联盟链

```
以太坊（公链）              FISCO BCOS（联盟链）
─────────────────────      ─────────────────────────
任何人可加入               需许可才能加入（KYC）
交易需 gas 费              无 gas，免费上链
TPS ~15                    TPS ~1000+
数据全部公开               可控数据可见性
出块时间 ~12s              出块时间 <1s（PBFT）
Solidity 同款              兼容 Solidity 语法
```

存证场景需要**法律可追溯的主体身份**（谁上链的？）和**低成本高频率写入**，
不需要公链的无许可特性，联盟链更合适。

### FISCO BCOS vs Hyperledger Fabric

| 特性 | FISCO BCOS | Hyperledger Fabric |
|------|-----------|-------------------|
| 智能合约语言 | Solidity（熟悉以太坊可直接用） | Go/Java/Node.js (Chaincode) |
| 国密支持 | 原生 SM2/SM3/SM4 | 需额外 patch |
| Java SDK | 官方维护，文档完整 | 较复杂 |
| 国内监管合规 | 商用密码认证 | 无国内认证 |
| 部署复杂度 | 一键脚本，4 节点 ~10分钟 | 复杂，需 Docker Compose 编排 |

项目使用国密模式，FISCO BCOS 是国内最成熟的选择。

---

## 2. 链的基本架构

### 本项目四节点部署

```
                    SDK Client (Java)
                         │
               ┌─────────┴─────────┐
               │   Channel 协议    │
               │  TLS + SM2 证书   │
               └────────┬──────────┘
                        │
          ┌─────────────┼─────────────┐
          │             │             │
     Node-0         Node-1       Node-2        Node-3
  127.0.0.1       127.0.0.1   127.0.0.1    127.0.0.1
  :20200           :20201      :20202        :20203
  P2P:30300       P2P:30301   P2P:30302    P2P:30303
          │             │             │         │
          └─────────────┴─────────────┴─────────┘
                    PBFT 共识（4 节点）
                    Group 1（单群组）
```

**Channel 端口**（20200-20203）：供 Java SDK 连接，使用 TLS + SM2 国密证书  
**P2P 端口**（30300-30303）：节点间通信，不对外暴露  
**JSON-RPC 端口**（8545）：仅供本机调试，不对外暴露

### PBFT 共识机制

```
发起交易
    │
    ▼
Pre-prepare：Leader 广播交易
    │
    ▼
Prepare：每个节点验证并广播确认
    │
    ▼
Commit：收到 2f+1 个 Prepare（f=1，即至少3个）
    │
    ▼
区块最终确认，写入本地账本
    │
    ▼
SDK 收到 TransactionReceipt
```

4 节点中最多容忍 **1 个节点故障**（f=1，需要 3f+1=4）。  
交易从发送到收到 receipt 通常在 **1 秒内**完成。

### 节点 ID（共识成员）

| 节点 | 端口 | Node ID（前16位） |
|------|------|-----------------|
| Node-0 | :20200 | `995bb4b9240286ff...` |
| Node-1 | :20201 | `8358832b8eeef1f1...` |
| Node-2 | :20202 | `4a6db5beb1b44ef8...` |
| Node-3 | :20203 | `80a0642910d62fec...` |

完整 Node ID 见 `/data/fisco/nodes/127.0.0.1/node{0-3}/conf/gmnode.nodeid`。

---

## 3. 国密算法

FISCO BCOS 国密模式用中国商用密码标准替换了以太坊的所有密码学原语：

| 以太坊标准 | 国密替代 | 用途 |
|-----------|---------|------|
| secp256k1 + ECDSA | SM2 | 账户私钥、交易签名 |
| Keccak-256 | SM3 | 哈希、交易 ID 计算 |
| AES-128 | SM4 | 通信加密 |
| TLS 1.2 | GMSSL（SM2+SM4） | SDK ↔ 节点信道加密 |

### 国密通道连接（双证书）

国密 TLS 握手需要两套证书（这是国密 TLS 的特殊要求）：

```
gmsdk.crt / gmsdk.key     ── 签名证书（用于身份认证，SM2签名）
gmensdk.crt / gmensdk.key ── 加密证书（用于密钥协商，SM2加密）
gmca.crt                  ── CA 根证书（验证两套证书的颁发者）
```

普通 TLS 只需一套证书；国密 TLS 分离了**签名**和**加密**职责，各用独立证书。

### SDK 配置

```toml
# config.toml
[cryptoMaterial]
certPath = "conf"        # 相对工作目录
useSMCrypto = true       # 启用国密

[network]
peers = ["127.0.0.1:20200", "127.0.0.1:20201",
         "127.0.0.1:20202", "127.0.0.1:20203"]
```

`useSMCrypto = true` 会让 SDK 使用 SM2 账户、SM3 哈希，并在 Channel 连接中使用 GMSSL。

---

## 4. 智能合约设计

文件：`src/main/resources/contracts/EvidenceContract.sol`  
已部署地址：`0xa0f88385434996c25d432464ffca1cedabaab5e0`  
部署交易：`0x5741fd94444e2f94d940788b34da442f08c8b84f0ae9e2094b72848abfd461db`  
合约所有者：`0x9d6037dcc8b3253c3f2a295eeac2d9fde804543c`

### 存证结构

```solidity
pragma solidity ^0.4.25;
pragma experimental ABIEncoderV2;

struct Evidence {
    string fileHash;      // 文件/Commit SHA-256（存证的核心标识）
    string fileName;      // 文件名或仓库@commitSHA[:7]
    string uploader;      // 上传者（DID 或 GitHub 用户名）
    uint256 fileSize;     // 文件字节数（代码存证时为 SHA 长度）
    string description;   // 描述（含分支、作者、提交信息）
    uint256 timestamp;    // block.timestamp（链上时间，秒级，不可伪造）
    bool exists;          // 防止重复存证
}

mapping(string => Evidence) private evidences;      // fileHash → Evidence
mapping(string => string[]) private userEvidences;  // uploader → fileHash[]
string[] private allHashes;                         // 全局哈希数组，支持分页查询
```

### 核心接口

```solidity
// 添加存证（幂等：同一 fileHash 只能存一次）
function addEvidence(
    string _fileHash, string _fileName, string _uploader,
    uint256 _fileSize, string _description
) public notEmpty(_fileHash) notEmpty(_fileName) notEmpty(_uploader)
  returns (bool success)

// 查询存证
function getEvidenceByHash(string _fileHash)
  returns (string, string, string, uint256, string, uint256)

// 验证存在性（O(1)，mapping 直查）
function verifyEvidence(string _fileHash) returns (bool exists)

// 分页查询（支持前端翻页）
function getEvidenceList(uint256 _offset, uint256 _limit)
  returns (string[] hashes)

// 批量验证
function batchVerifyEvidence(string[] _fileHashes) returns (bool[] results)
```

### 事件

```solidity
event EvidenceAdded(
    string  fileHash,
    string  fileName,
    string  uploader,
    uint256 fileSize,
    uint256 timestamp   // block.timestamp，链上可验证
);
```

每次成功上链都会触发 `EvidenceAdded` 事件，可用于链外监听和通知。

### 设计约定

- `fileHash` 是 **全局唯一键**：重复上链同一哈希会被 `require(!evidences[_fileHash].exists)` 拒绝
- 文件存证：`fileHash = SHA-256(文件内容)`，算法字段 `hashAlgorithm = "SHA-256"`
- 代码存证（GitHub）：`fileHash = Git Commit SHA-1`，算法字段 `hashAlgorithm = "GIT-SHA1"`
- `timestamp` 使用 `block.timestamp`，精度秒级，由矿工/共识节点设置，不依赖客户端时钟

---

## 5. Java SDK 接入

### Bean 初始化

`BcosSDKConfig.java` 负责创建 SDK 单例：

```java
@Bean
public BcosSDK bcosSDK() throws BcosSDKException {
    ConfigOption configOption = new ConfigOption();
    // 加载 config.toml（从 classpath 或工作目录）
    configOption.setCryptoMaterialConfig(cryptoMaterialConfig);
    configOption.setNetworkConfig(networkConfig);
    return new BcosSDK(configOption);
}

@Bean
public Client client(BcosSDK sdk) {
    return sdk.getClient(1);   // Group 1
}

@Bean
public CryptoKeyPair cryptoKeyPair(Client client) {
    return client.getCryptoSuite().getKeyPairFactory().generateKeyPair();
}
```

`getClient(1)` 中的 `1` 是 Group ID，对应 `conf/group.1.genesis`。

### 合约调用（BlockchainServiceImpl）

```java
// 加载已部署的合约（不重新部署）
EvidenceContract contract = EvidenceContract.load(
    "0xa0f88385434996c25d432464ffca1cedabaab5e0",
    client,
    cryptoKeyPair
);

// 写操作（发送交易，消耗 gas，但 FISCO BCOS 联盟链 gas = 0）
TransactionReceipt receipt = contract.addEvidence(
    fileHash, fileName, uploader, fileSize.toString(), description
);

// 读操作（call，不上链，免费）
Boolean exists = contract.verifyEvidence(fileHash);
```

### TransactionReceipt 解析

```java
receipt.getTransactionHash()   // 0x开头的交易哈希（64位hex）
receipt.getBlockNumber()       // 十六进制字符串，如 "0xe"（表示第14块）
receipt.getStatus()            // "0x0" = 成功，其他 = revert
receipt.getOutput()            // ABI 编码的返回值
```

`blockNumber` 是十六进制，需要手动转换：

```java
private Long parseBlockNumber(String hex) {
    return hex.startsWith("0x")
        ? Long.parseLong(hex.substring(2), 16)
        : Long.valueOf(hex);
}
```

### verifyEvidence 的特殊处理

直接调用合约 `verifyEvidence()` 会抛出 `ContractException`（ABI 解析问题），
因此 `EvidenceServiceImpl` 优先走数据库校验：

```java
public boolean verifyEvidence(String fileHash) {
    FileEvidence ev = getOne(wrapper.eq(FileEvidence::getFileHash, fileHash));
    if (ev == null) return false;

    // DB 有记录 + 已成功上链，直接返回 true
    if (ev.getTransactionHash() != null && !ev.getTransactionHash().isEmpty()
            && ev.getChainStatus() == Constants.CHAIN_STATUS_SUCCESS) {
        return true;
    }

    // 降级：尝试调用合约（兜底，通常不走这里）
    try {
        return blockchainService.verifyEvidence(fileHash);
    } catch (Exception e) {
        log.warn("合约验证失败，回退到DB验证: {}", e.getMessage());
        return false;
    }
}
```

---

## 6. 异步上链队列

### 为什么需要异步

FISCO BCOS 交易上链需要等待 PBFT 共识完成，通常耗时 **500ms ~ 2s**。
如果同步处理，文件上传接口会阻塞用户等待 2 秒以上。
用 Redis 队列解耦，上传接口立即返回，上链在后台异步完成。

### 完整流程

```
用户上传文件
    │
    ▼
EvidenceServiceImpl.uploadEvidence()
    ├─ SHA-256 计算文件哈希
    ├─ MinIO 存储文件
    ├─ 写 file_evidence（chain_status = 0 = PENDING）
    └─ pushBlockchainTask(task) → Redis List "blockchain_tasks"
    │
    ▼ 立即返回给用户（文件已保存，上链进行中）

━━━━━━━━━━━━━━━━ 后台异步 ━━━━━━━━━━━━━━━━

AsyncTaskServiceImpl.consumeBlockchainTasks()
    │ 轮询 Redis，popBlockchainTask()
    ▼
processBlockchainTask(task)
    ├─ blockchainService.addEvidence(...)
    │   └─ EvidenceContract.addEvidence(...)  ← FISCO BCOS 交易
    │
    ├─ 成功：UPDATE file_evidence
    │         SET transaction_hash = '0x...',
    │             block_number = 14,
    │             chain_status = 1   ← SUCCESS
    │
    └─ 失败：重试（最多3次，指数退避 5s/15s/30s）
              超过3次：chain_status = 2   ← FAILED
```

### Redis 队列结构

```java
// 区块链上链任务
public static class BlockchainTask {
    private Long   evidenceId;   // file_evidence.id
    private String fileHash;     // 上链的哈希值
    private String fileName;
    private String uploader;
    private Long   fileSize;
    private String description;
    private int    retryCount;   // 重试计数，初始为 0
}
```

Redis key：`blockchain:task:queue`（List，LPUSH 入队，RPOP 出队，FIFO）

### 重试策略

| 第几次失败 | 等待时间 | 计算方式 |
|----------|---------|---------|
| 第1次    | 5s      | 3^1 × 5 |
| 第2次    | 15s     | 3^2 × 5 |
| 第3次    | 30s（终止）| 3^3 × 5，超限标记失败 |

失败常见原因：FISCO BCOS SDK `@Lazy` 初始化未完成（首次请求），或节点暂时不可达。

---

## 7. GitHub Webhook 自动存证

### 触发流程

```
GitHub 仓库 push 事件
    │ HTTP POST
    ▼
POST /webhook/github
    ├─ 验证 HMAC-SHA256 签名（防伪造）
    └─ WebhookServiceImpl.processGithubPush(payload)
            │
            ├─ 解析 commits 数组
            ├─ 去重（commit SHA 已存证则跳过）
            ├─ 写 file_evidence（fileHash = Commit SHA）
            └─ pushBlockchainTask → Redis → FISCO BCOS 上链
```

### Webhook 签名验证

GitHub 在每次请求中带 `X-Hub-Signature-256` 头：

```
X-Hub-Signature-256: sha256=<HMAC-SHA256(secret, payload_body)>
```

服务端用相同 secret 重新计算，常量时间比对，防止时序攻击：

```java
String computed = "sha256=" + hmacSha256(webhookSecret, payload);
if (!MessageDigest.isEqual(computed.getBytes(), signature.getBytes())) {
    return 403;
}
```

### Commit SHA 作为存证哈希

GitHub Commit SHA 是 Git 的 SHA-1 哈希（40位十六进制），覆盖：

```
SHA-1(
    "commit\0"
    + tree <tree-sha>\n
    + parent <parent-sha>\n  （可选）
    + author <name> <email> <timestamp>\n
    + committer <name> <email> <timestamp>\n
    + \n
    + <commit message>
)
```

其中包含了**文件树哈希**（tree SHA），因此任何文件内容变化都会导致 Commit SHA 不同。
这意味着链上存储的 Commit SHA 间接证明了代码内容的完整性。

### 存入数据库的字段映射

```
file_evidence 字段          ← GitHub Webhook payload
─────────────────────────────────────────────────────────
file_hash                  ← commit.id（40位 commit SHA）
file_name                  ← "{owner}/{repo}@{sha[:7]}"
git_repository_path        ← "github://{owner}/{repo}"
git_commit_hash            ← commit.id
git_commit_message         ← commit.message
git_author_name            ← commit.author.name
git_author_email           ← commit.author.email
git_branch_name            ← ref 去掉 "refs/heads/" 前缀
description                ← "[{branch}] {author}: {message}"
hash_algorithm             ← "GIT-SHA1"
git_status                 ← 2（远程来源）
chain_status               ← 0（PENDING，等待上链）
```

### GitHub 配置步骤

1. 仓库 → Settings → Webhooks → Add webhook
2. Payload URL：`http://107.172.134.19:8080/webhook/github`
3. Content type：`application/json`
4. Secret：与 `application.yml` 中 `app.webhook.github.secret` 保持一致
5. Events：勾选 "Just the push event"

---

## 8. API 接口

### 区块链查询接口

```
GET  /api/blockchain/block-number        当前区块高度
GET  /api/blockchain/block/{height}      指定高度的区块信息（JSON字符串）
GET  /api/blockchain/transaction/{hash}  交易详情
GET  /api/blockchain/nodes               节点状态列表
GET  /api/blockchain/info                合约地址、SDK连接状态、区块高度
```

**获取区块示例：**

```bash
curl http://localhost:8080/api/blockchain/block/14
```

```json
{
  "code": 200,
  "data": "{\"number\":14,\"hash\":\"0x2cddd0fe...\",\"parentHash\":\"0x50c0daa7...\",
            \"transactionsRoot\":\"0x...\",\"stateRoot\":\"0x...\",
            \"timestamp\":\"0x19681b7e8b2\",
            \"gasLimit\":\"0x...\",\"gasUsed\":\"0x0\",
            \"transactions\":[\"0xabcd...\"],\"size\":128}"
}
```

注意：`timestamp` 是十六进制字符串，前端需要 `new Date(parseInt(ts, 16))` 转换。

### 存证接口

```
POST /api/evidence/upload                 文件上传 + 异步上链
GET  /api/evidence/list                   存证列表
GET  /api/file-evidence/verify/{hash}     验证存证有效性（优先 DB，降级合约）
GET  /api/code/evidence                   代码存证列表（含 GitHub 记录）
POST /webhook/github                      GitHub push 事件入口
```

**验证存证示例：**

```bash
curl http://localhost:8080/api/file-evidence/verify/351efe4b3e6a...
```

```json
{
  "code": 200,
  "message": "存证有效",
  "data": true
}
```

---

## 9. 证书体系

### 证书目录

```
src/main/resources/conf/     ← Spring Boot 打包入 classpath
├── ca.crt                   # 普通 TLS CA 根证书
├── sdk.crt                  # SDK 普通 TLS 客户端证书
├── sdk.key                  # SDK 普通 TLS 私钥（不提交 git）
├── gmca.crt                 # 国密 CA 根证书
├── gmsdk.crt                # 国密 SDK 签名证书
├── gmsdk.key                # 国密 SDK 签名私钥（不提交 git）
├── gmensdk.crt              # 国密 SDK 加密证书
└── gmensdk.key              # 国密 SDK 加密私钥（不提交 git）
```

生产节点的证书来源：`/data/fisco/nodes/127.0.0.1/sdk/` 和 `sdk/gm/`

### 为什么私钥不提交 git

`gmsdk.key`、`gmensdk.key`、`sdk.key` 已加入 `.gitignore`。  
私钥泄露后，攻击者可以伪装成合法 SDK 客户端连接节点、发送任意交易。  
部署新环境时，从节点 SDK 目录手动复制：

```bash
cp /data/fisco/nodes/127.0.0.1/sdk/sdk.key           src/main/resources/conf/
cp /data/fisco/nodes/127.0.0.1/sdk/gm/gmsdk.key      src/main/resources/conf/
cp /data/fisco/nodes/127.0.0.1/sdk/gm/gmensdk.key    src/main/resources/conf/
```

### SDK 工作目录与 certPath

`config.toml` 中 `certPath = "conf"` 是相对路径，相对于 **JVM 工作目录**（`user.dir`）。  
打包后 cert 文件在 classpath 内（JAR 中），SDK 无法通过相对路径读取 JAR 内部文件。  
解决方案：同时在工作目录下保留一份 `conf/`：

```bash
# 在 FBEvidence-master/ 目录下运行时
mkdir -p conf
cp src/main/resources/conf/* conf/
```

---

## 10. 节点配置与部署

### 节点目录结构

```
/data/fisco/nodes/127.0.0.1/
├── fisco-bcos              # FISCO BCOS 可执行文件
├── node0/
│   ├── config.ini          # 节点配置（端口、P2P、账本）
│   ├── conf/
│   │   ├── group.1.genesis  # 创世块（共识成员、block size 等，不可改）
│   │   ├── group.1.ini      # 运行时配置（可热更新）
│   │   ├── gmnode.key       # 节点 SM2 私钥
│   │   └── gmnode.nodeid    # 节点 ID（SM2 公钥的派生）
│   ├── start.sh / stop.sh
│   └── fisco-bcos -> ../fisco-bcos
├── node1/ ... node2/ ... node3/
└── sdk/                    # SDK 证书（供 Java SDK 使用）
    ├── ca.crt  sdk.crt  sdk.key
    └── gm/
        ├── gmca.crt  gmsdk.crt  gmsdk.key
        └── gmensdk.crt  gmensdk.key
```

### 关键配置参数（group.1.genesis，创世块，不可修改）

```ini
[consensus]
consensus_type = pbft              # 共识算法
consensus_timeout = 3              # 共识超时秒数
max_trans_num = 1000               # 每块最多交易数
node.0 = 995bb4b9...               # 4个共识节点 ID
node.1 = 8358832b...
node.2 = 4a6db5be...
node.3 = 80a06429...

[storage]
type = rocksdb                     # 底层存储引擎

[tx]
gas_limit = 300000000              # 联盟链 gas 上限（不收费，设大即可）
```

### 启停命令

```bash
# 启动所有节点
bash /data/fisco/nodes/127.0.0.1/node0/start.sh
bash /data/fisco/nodes/127.0.0.1/node1/start.sh
bash /data/fisco/nodes/127.0.0.1/node2/start.sh
bash /data/fisco/nodes/127.0.0.1/node3/start.sh

# 查看节点日志（实时）
tail -f /data/fisco/nodes/127.0.0.1/node0/log/log_*.log | grep -E "Sealing|Sealed|consensus"

# 验证节点正在出块
curl -s http://127.0.0.1:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"getBlockNumber","params":[1],"id":1}'
```

### 启动 Spring Boot 应用

```bash
cd /root/data/FBEvidence-master

# 确保 conf/ 目录有证书
cp src/main/resources/conf/* conf/ 2>/dev/null

# 启动（国密模式需要 Java 11+）
nohup java \
  -Dfile.encoding=UTF-8 \
  -Dspring.profiles.active=dev \
  -jar target/blockchain-evidence-system-1.0.0.jar \
  > app.log 2>&1 &

# 验证启动成功
sleep 15 && curl http://localhost:8080/api/blockchain/block-number
```

---

## 11. 链上数据与数据库对照

### 存证状态流转

```
用户上传文件
    │
    ▼
chain_status = 0 (PENDING)    ← 写入DB，等待上链
transaction_hash = NULL
block_number = NULL
    │
    ▼ 异步队列处理（约0.5~2秒）
    │
    ├─ 成功 ──▶  chain_status = 1 (SUCCESS)
    │            transaction_hash = "0xabcd..."
    │            block_number = 14
    │
    └─ 失败×3 ─▶ chain_status = 2 (FAILED)
                 chain_message = "上链失败: ..."
```

### `file_evidence` 关键字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `file_hash` | VARCHAR(64) | SHA-256（文件）或 Commit SHA-1（代码），链上的 key |
| `transaction_hash` | VARCHAR(66) | FISCO BCOS 交易哈希，0x 开头，66位 |
| `block_number` | BIGINT | 交易所在区块高度，十进制存储 |
| `chain_status` | TINYINT | 0=PENDING, 1=SUCCESS, 2=FAILED |
| `chain_message` | VARCHAR(255) | 上链结果描述或错误原因 |
| `hash_algorithm` | VARCHAR(32) | "SHA-256" 或 "GIT-SHA1" |
| `git_repository_path` | VARCHAR(255) | "github://{owner}/{repo}" 或本地路径 |
| `git_status` | TINYINT | 1=本地Git, 2=GitHub远程 |

### 链上 vs 链下存储对比

```
链上存储（EvidenceContract.sol）
─────────────────────────────────
fileHash（string）    ← 唯一键，O(1)查询
fileName（string）
uploader（string）
fileSize（uint256）
description（string）
timestamp（uint256）  ← block.timestamp，防篡改

链下存储（MySQL file_evidence）
─────────────────────────────────
+ transaction_hash    ← 用于验证（链上的证明）
+ block_number        ← 链上位置
+ chain_status        ← 异步状态追踪
+ commitment_hash     ← ZK 承诺（可选）
+ file_path           ← MinIO 存储路径（链上不存大文件）
+ user_id             ← 系统用户 ID（链上只有字符串 uploader）
+ 所有 git_* 字段     ← Git/GitHub 元数据（链上只存 fileHash）
```

设计原则：**链上只存验证必需的哈希和时间戳**，完整元数据存链下，
通过 `transaction_hash` 双向验证。

---

## 12. 常见问题排查

### SDK 连接失败：`init channel network error`

```
原因 1：证书不匹配
  检查：openssl x509 -in src/main/resources/conf/gmca.crt -noout -fingerprint
        对比 /data/fisco/nodes/127.0.0.1/sdk/gm/gmca.crt 的指纹
  修复：重新从节点 sdk/ 目录复制所有证书（含私钥）

原因 2：节点未启动
  检查：lsof -i:20200
  修复：bash /data/fisco/nodes/127.0.0.1/node0/start.sh（以此类推）

原因 3：certPath 相对路径解析到错误位置
  检查：System.getProperty("user.dir") 的输出
  修复：在工作目录创建 conf/ 并复制证书
```

### 交易发送成功但 status 非 0x0

```
receipt.getStatus() = "0x16" 等非零值表示合约 revert：
  - 0x16：require 失败（如重复存证："Evidence already exists"）
  - 检查 receipt.getMessage() 获取 revert reason

处理方式：
  if (!"0x0".equals(receipt.getStatus())) {
      throw new RuntimeException("合约执行失败: " + receipt.getOutput());
  }
```

### 第一次上链慢（5~10秒）

原因：`@Lazy` 注解延迟了 FISCO BCOS SDK 初始化，第一次调用时才建立 TLS 连接。  
影响：只影响应用启动后的第一次上链，后续正常（<1s）。  
若要消除：去掉 `BlockchainServiceImpl` 中的 `@Lazy`，但需确保节点在 Spring Boot 启动时已运行，否则启动报错。

### Redis 队列积压

```bash
# 查看队列长度
redis-cli llen blockchain:task:queue

# 手动检查队列内容
redis-cli lrange blockchain:task:queue 0 -1
```

积压原因通常是 FISCO BCOS 节点异常，等节点恢复后队列会自动消费。

### 区块浏览器 timestamp 显示异常

FISCO BCOS 返回的 `block.timestamp` 是十六进制字符串（如 `"0x19681b7e8b2"`），
单位是**毫秒**（不是秒）：

```javascript
// 正确转换
const ts = parseInt(blockData.timestamp, 16);  // 毫秒
new Date(ts).toLocaleString('zh-CN');
```

---

*FISCO BCOS 版本：2.9.1 | Java SDK：2.9.1 | 合约：Solidity 0.4.25 | 部署日期：2026-05*
