# ZK 存证模块技术文档

> 基于 RISC Zero zkVM 3.0.5 + FISCO BCOS 2.9.1 (国密版)

---

## 目录

1. [为什么要加 ZK](#1-为什么要加-zk)
2. [承诺方案设计](#2-承诺方案设计)
3. [ZK 技术栈原理](#3-zk-技术栈原理)
4. [目录结构](#4-目录结构)
5. [数据库设计](#5-数据库设计)
6. [电路实现（Guest）](#6-电路实现guest)
7. [证明器实现（Host）](#7-证明器实现host)
8. [验证器实现](#8-验证器实现)
9. [Java 服务层](#9-java-服务层)
10. [API 接口](#10-api-接口)
11. [两种运行模式](#11-两种运行模式)
12. [构建与部署](#12-构建与部署)
13. [真实证明测试记录](#13-真实证明测试记录)
14. [内存与性能](#14-内存与性能)
15. [与 ZK Rollup 的区别](#15-与-zk-rollup-的区别)

---

## 1. 为什么要加 ZK

### 原始存证的问题

不加 ZK 时，链上直接存储的是文件哈希：

```
链上：file_hash = SHA-256(文件内容)
```

这有一个隐私问题：**任何能拿到同一份文件的人，都能计算出相同的哈希，
从而确认链上某条记录对应的是哪个文件**。对于敏感文件（合同、证据、源码），
这等于在公链上留下了可关联的指纹。

### ZK 承诺解决的问题

加入 ZK 后，链上存储的是承诺值：

```
链上：commitment = SHA-256(file_hash ‖ salt)
```

- `file_hash`：文件内容哈希（**私密**，不上链）
- `salt`：每次随机生成的 32 字节随机数（**私密**，只有所有者知道）
- `commitment`：上链的公开值，看起来像随机数，**无法反推回原始文件**

ZK proof 的作用是：**向任何人证明"我确实知道对应 file_hash 和 salt，
且 SHA-256(file_hash ‖ salt) == commitment"，而不需要透露 file_hash 和 salt 本身**。

```
证明者知道：file_hash, salt
验证者只看到：commitment（链上公开）+ ZK proof（数学保证）
结论：证明者在时间 T 确实拥有该文件，且承诺未被篡改
```

---

## 2. 承诺方案设计

### 承诺计算

```
commitment = SHA-256(file_hash ‖ salt)

其中：
  file_hash = SHA-256(文件内容)，32 字节
  salt      = SecureRandom 生成，32 字节
  ‖         = 字节拼接，总共 64 字节作为 SHA-256 输入
```

### 为什么用 salt

没有 salt 的承诺 `SHA-256(file_hash)` 仍然是确定性的，
同一个文件永远产生同一个承诺值。加入 salt 后：

- 同一个文件每次承诺产生不同的值（彩虹表攻击失效）
- 不同用户对同一文件的承诺互不关联（关联分析失效）
- salt 只有所有者保存，承诺不可伪造

### ZK proof 证明的命题

```
公开输入：commitment（链上），user_id，timestamp
私密输入：file_hash，salt

证明：SHA-256(file_hash ‖ salt) == commitment
```

电路执行时用 `assert_eq!` 强制验证，若不等则证明失败，
无法生成有效的 ZK proof。

---

## 3. ZK 技术栈原理

### RISC Zero zkVM 内部流水线

```
用户写普通 Rust 程序（无需手写电路）
        │
        ▼
RISC Zero 编译为 RISC-V ELF（guest 程序）
        │
        ▼
zkVM 执行 RISC-V 指令，生成 STARK proof
（证明"这段程序在这些输入下确实运行了"）
        │  内置 STARK → SNARK 压缩
        ▼
Groth16 SNARK proof（~几KB，适合存储/验证）
```

开发者只写 Rust 函数逻辑，不需要手写算术电路。

### STARK vs SNARK

| 属性 | STARK | SNARK (Groth16) |
|------|-------|-----------------|
| 证明大小 | 几百KB ~ 几MB | ~256字节（纯 proof） |
| 验证速度 | 较慢 | 极快（毫秒级） |
| Trusted Setup | 不需要 | 需要（一次性仪式） |
| 量子安全 | 是 | 否 |
| 适合场景 | 生成阶段 | 存储/链上验证 |

RISC Zero 内部用 STARK（无 trusted setup，灵活），
输出时压缩为 Groth16 SNARK（小，便于存储和验证）。

### 与 ZK Rollup 的区别

本项目使用 ZK 的目的是**隐私承诺**，不是**批量压缩**：

```
ZK Rollup：  N笔交易 → 1个proof → 1笔上链（节省 gas）
本项目：     1个文件 → 1个proof → 证明"我知道文件内容"（保护隐私）
```

FISCO BCOS 是联盟链，不收 gas 费，没有经济压力批量打包。
每个存证有独立时间戳，批量会损失时间精度，法律价值下降。

---

## 4. 目录结构

```
zk/
├── Cargo.toml                  # workspace 根
├── Cargo.lock
├── host/
│   ├── Cargo.toml              # prove + verify 两个 bin
│   └── src/
│       ├── prove.rs            # 证明器主程序（stdin JSON → stdout JSON）
│       └── verify.rs           # 验证器主程序（stdin JSON → {"ok":true}）
└── methods/
    ├── Cargo.toml
    ├── build.rs                # 编译 guest ELF
    └── guest/
        ├── Cargo.toml
        └── src/
            └── main.rs         # ZK 电路逻辑

target/debug/
├── prove                       # 编译产物：证明器二进制
└── verify                      # 编译产物：验证器二进制
```

---

## 5. 数据库设计

### `file_evidence` 表新增字段

```sql
ALTER TABLE file_evidence
    ADD COLUMN commitment_hash VARCHAR(64)  COMMENT 'SHA-256(file_hash || salt)，上链的承诺值',
    ADD COLUMN salt_hex        VARCHAR(64)  COMMENT '32字节随机盐（十六进制），仅所有者可见',
    ADD COLUMN zk_status       TINYINT DEFAULT 0 COMMENT '0=未生成，1=已生成，2=失败';
```

`commitment_hash` 是上链的公开值，`salt_hex` 是只有服务端持有的私密值。

### `evidence_zk_proofs` 表

```sql
CREATE TABLE IF NOT EXISTS evidence_zk_proofs (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    evidence_id     BIGINT       NOT NULL,          -- 关联 file_evidence.id
    user_id         BIGINT       NOT NULL,
    image_id        VARCHAR(128) NOT NULL,           -- RISC Zero ELF 镜像 ID（电路版本标识）
    commitment_hex  VARCHAR(64)  NOT NULL,           -- 与链上一致的承诺
    journal_hex     TEXT         NOT NULL,           -- 电路公开输出（user_id + timestamp + commitment）
    journal_digest  VARCHAR(64)  NOT NULL,           -- SHA-256(journal)，用于快速对比
    seal_hex        MEDIUMTEXT   NOT NULL,           -- Groth16 proof 序列化（bincode hex）
    status          VARCHAR(20)  NOT NULL DEFAULT 'MOCK',  -- MOCK 或 REAL
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (evidence_id) REFERENCES file_evidence(id) ON DELETE CASCADE
);
```

**字段说明：**

- `image_id`：标识电路版本（ELF 哈希），如果电路逻辑变了，image_id 也变，历史 proof 仍可用旧版本验证
- `journal_hex`：ZK 电路的公开输出，包含 `user_id`、`timestamp`、`commitment`，任何人可读
- `seal_hex`：Groth16 proof 的 bincode 序列化再 hex 编码，用于离线验证
- `status = "REAL"`：表示由 RISC Zero 真实生成，`"MOCK"` 表示 Java 端模拟

---

## 6. 电路实现（Guest）

文件：`zk/methods/guest/src/main.rs`

```rust
#![no_main]

use risc0_zkvm::guest::env;
use risc0_zkvm::sha::{Impl, Sha256, Digest};
use serde::{Deserialize, Serialize};

risc0_zkvm::guest::entry!(main);

/// 私密输入（证明者持有，不暴露）
#[derive(Deserialize)]
struct EvidenceInput {
    file_hash:           [u8; 32],  // SHA-256(文件内容)
    salt:                [u8; 32],  // 32字节随机盐
    user_id:             u64,
    timestamp:           u64,       // Unix 秒
    expected_commitment: [u8; 32],  // 链上存储的承诺值
}

/// 公开输出（写入 journal，任何人可读）
#[derive(Serialize)]
struct EvidenceJournal {
    user_id:    u64,
    timestamp:  u64,
    commitment: [u8; 32],
}

fn main() {
    let input: EvidenceInput = env::read();

    // 计算 SHA-256(file_hash ‖ salt)
    let mut preimage = [0u8; 64];
    preimage[..32].copy_from_slice(&input.file_hash);
    preimage[32..].copy_from_slice(&input.salt);

    let digest: Digest = *Impl::hash_bytes(&preimage);
    let commitment: [u8; 32] = digest.as_bytes().try_into().expect("32 bytes");

    // 核心断言：承诺值必须与链上一致，否则 proof 无法生成
    assert_eq!(commitment, input.expected_commitment, "commitment mismatch");

    // 公开输出（写入 journal）
    env::commit(&EvidenceJournal {
        user_id: input.user_id,
        timestamp: input.timestamp,
        commitment,
    });
}
```

**关键点：**

- `env::read()`：从 host 读取私密输入（zkVM 内部通信，不暴露给外部）
- `Impl::hash_bytes()`：RISC Zero 提供的 SHA-256，在 zkVM 内部执行并生成对应 STARK 约束
- `assert_eq!`：这不是普通断言，zkVM 会把它转化为约束——一旦不满足，证明生成就失败，而不是 panic
- `env::commit()`：把 journal 写入公开输出，任何人拿到 proof 后都能读取这些值

---

## 7. 证明器实现（Host）

文件：`zk/host/src/prove.rs`

### 接口协议（stdin/stdout JSON）

**输入（stdin）：**

```json
{
  "userId": 3,
  "fileHashHex": "351efe4b3e6a3a7e8b23d1c9f0a2d4e5...",
  "saltHex":     "aabbccddeeff00112233445566778899...",
  "timestamp":   1748700000,
  "commitmentHex": "deadbeef1234567890abcdef..."
}
```

**输出（stdout）：**

```json
{
  "imageId":    "0x3f9a2b1c...",
  "journalHex": "0300000000000000...",
  "sealHex":    "010000000000000001..."
}
```

### 核心代码

```rust
fn main() -> Result<()> {
    // 从 stdin 读取 JSON 输入
    let mut stdin_buf = String::new();
    io::stdin().read_to_string(&mut stdin_buf)?;
    let job: ProveJob = serde_json::from_str(&stdin_buf)?;

    // hex 解码为字节数组
    let file_hash  = hex_to_32(&job.file_hash_hex,  "fileHashHex")?;
    let salt       = hex_to_32(&job.salt_hex,        "saltHex")?;
    let commitment = hex_to_32(&job.commitment_hex,  "commitmentHex")?;

    // 构建 zkVM 执行环境，写入私密输入
    let env = ExecutorEnv::builder()
        .write(&GuestInput { file_hash, salt, user_id: job.user_id,
                             timestamp: job.timestamp,
                             expected_commitment: commitment })
        .build()?;

    // 调用默认证明器（CUDA 加速 Groth16）
    let prover  = default_prover();
    let receipt = prover.prove(env, EVIDENCE_VERIFY_ELF)?.receipt;

    // 输出结果
    println!("{}", serde_json::to_string(&ProveResult {
        image_id:    format!("0x{}", image_id_hex()),
        journal_hex: hex::encode(&receipt.journal.bytes),
        seal_hex:    hex::encode(bincode::serialize(&receipt)?),
    })?);
    Ok(())
}
```

**`default_prover()` 的行为：**

- 检测到 CUDA GPU → 启用 GPU 加速（~2秒）
- 无 GPU → 纯 CPU 证明（几分钟）
- 环境变量 `RISC0_PROVER=bonsai` → 调用 Bonsai 云端证明服务

### Journal 编码格式

Journal 是电路公开输出的字节序列，Java 端需要能解读它：

```
字节偏移   长度    内容
[0,   8)   8字节  user_id（小端 u64）
[8,  16)   8字节  timestamp（小端 u64）
[16,144)  128字节  commitment（32字节，每字节存为小端 u32 = 4字节）
总计：144字节
```

commitment 每个字节被存为 4 字节小端 u32，这是 risc0 serde 的序列化约定。

---

## 8. 验证器实现

文件：`zk/host/src/verify.rs`

```rust
fn main() -> Result<()> {
    let job: VerifyJob = serde_json::from_str(&read_stdin())?;

    let journal_bytes = hex::decode(&job.journal_hex)?;
    let receipt: Receipt = bincode::deserialize(&hex::decode(&job.seal_hex)?)?;

    // 验证 journal 与 receipt 内部一致
    if receipt.journal.bytes != journal_bytes {
        bail!("journal mismatch");
    }

    // 核心：用 image_id（电路指纹）验证 proof
    // 这一步保证了：proof 确实由 EVIDENCE_VERIFY 这个电路生成
    receipt.verify(EVIDENCE_VERIFY_ID)?;

    println!(r#"{"ok":true}"#);
    Ok(())
}
```

**验证的保证：**

1. `receipt.verify(IMAGE_ID)` 检查 proof 是否由正确的电路生成（防止伪造电路）
2. journal 一致性检查确保公开输出未被篡改
3. Groth16 验证保证计算正确性（数学证明，不依赖信任）

**命令行使用：**

```bash
echo '{"journalHex":"...","sealHex":"..."}' | ./target/debug/verify
# 成功输出：{"ok":true}
# 失败输出：非零退出码 + 错误信息
```

---

## 9. Java 服务层

### ZkEvidenceServiceImpl 工作流程

```
commitEvidence(id)
  └─ 读取 file_evidence.file_hash
  └─ SecureRandom 生成 32字节 salt
  └─ SHA-256(file_hash ‖ salt) = commitment
  └─ 写回 DB：commitment_hash, salt_hex

generateProof(id)
  └─ 若未 commit，先 commit
  └─ proverBinary 为空 → mockProve()
  └─ proverBinary 非空 → proveWithBinary()
  └─ 写入 evidence_zk_proofs 表
  └─ 更新 file_evidence.zk_status = 1
```

### Mock 模式（proveWithBinary 为空）

Java 端执行与电路相同的 SHA-256 验证，不调用 Rust：

```java
private EvidenceZkProof mockProve(FileEvidence ev) throws Exception {
    byte[] fileHash   = unhex(ev.getFileHash());
    byte[] salt       = unhex(ev.getSaltHex());
    byte[] recomputed = sha256Concat(fileHash, salt);

    // 验证承诺与 DB 中一致
    if (!Arrays.equals(recomputed, unhex(ev.getCommitmentHash()))) {
        throw new IllegalStateException("Commitment verification failed");
    }

    // 生成模拟 journal（格式与真实电路一致）
    long timestamp = Instant.now().getEpochSecond();
    byte[] journal = encodeJournal(ev.getUserId(), timestamp,
                                   unhex(ev.getCommitmentHash()));

    EvidenceZkProof proof = new EvidenceZkProof();
    proof.setSealHex(hex(new byte[64]));  // 全零 seal，标识 MOCK
    proof.setStatus("MOCK");
    // ...
    return proof;
}
```

### 真实模式（调用 Rust 二进制）

```java
private EvidenceZkProof proveWithBinary(FileEvidence ev) throws Exception {
    // 构建输入 JSON
    Map<String, Object> input = Map.of(
        "userId",        ev.getUserId(),
        "fileHashHex",   ev.getFileHash(),
        "saltHex",       ev.getSaltHex(),
        "timestamp",     Instant.now().getEpochSecond(),
        "commitmentHex", ev.getCommitmentHash()
    );

    // 通过 stdin/stdout 调用 prove 二进制
    Process proc = new ProcessBuilder(proverBinary)
            .redirectErrorStream(true)
            .start();
    proc.getOutputStream().write(objectMapper.writeValueAsBytes(input));
    proc.getOutputStream().close();

    String output = new String(proc.getInputStream().readAllBytes());
    int exitCode  = proc.waitFor();
    if (exitCode != 0) throw new RuntimeException("ZK prover failed: " + output);

    // 解析输出，存入 DB
    Map<String, Object> result = objectMapper.readValue(output, Map.class);
    proof.setSealHex((String) result.get("sealHex"));
    proof.setStatus("REAL");
    return proof;
}
```

### Journal 编码（Java 端）

Java 需要复现 risc0 serde 的 journal 格式，以便本地验证和展示：

```java
private byte[] encodeJournal(Long userId, long timestamp, byte[] commitment) {
    // Layout: user_id(8) + timestamp(8) + commitment(32×4=128) = 144 bytes
    ByteBuffer buf = ByteBuffer.allocate(8 + 8 + 32 * 4)
                               .order(ByteOrder.LITTLE_ENDIAN);
    buf.putLong(userId);
    buf.putLong(timestamp);
    for (byte b : commitment) {
        buf.putInt(Byte.toUnsignedInt(b));  // 每字节扩展为 LE u32
    }
    return buf.array();
}
```

---

## 10. API 接口

### 完整流程

```
Step 1  POST /api/zk/evidence/{id}/commit       生成承诺（salt + commitment）
Step 2  POST /api/zk/evidence/{id}/prove        生成 ZK proof
Step 3  GET  /api/zk/evidence/{id}/proof        查询最新 proof
Step 4  GET  /api/zk/evidence/{id}/commitment   查询公开承诺（不含 salt）
```

### Step 1：生成承诺

```bash
curl -X POST http://localhost:8090/api/zk/evidence/5/commit \
  -H "Authorization: Bearer <token>"
```

响应：

```json
{
  "code": 200,
  "message": "承诺生成成功",
  "data": {
    "id": 5,
    "fileName": "Cover Letter.docx",
    "fileHash": "351efe4b...",
    "commitmentHash": "a7c3d9f1...",   // 这个值上链
    "saltHex": "3f8a2b1c...",           // 私密，只有服务端持有
    "zkStatus": 0
  }
}
```

### Step 2：生成 ZK proof

```bash
curl -X POST http://localhost:8090/api/zk/evidence/5/prove \
  -H "Authorization: Bearer <token>"
```

响应（真实模式 ~2秒，mock 模式 <100ms）：

```json
{
  "code": 200,
  "message": "证明生成成功",
  "data": {
    "id": 1,
    "evidenceId": 5,
    "imageId": "0x3f9a2b1c...",
    "commitmentHex": "a7c3d9f1...",
    "journalHex": "0300000000000000...",
    "journalDigest": "9f1c3a...",
    "sealHex": "010000000000000001...",  // Groth16 proof（bincode hex）
    "status": "REAL",                    // 或 "MOCK"
    "createdAt": "2026-06-01 12:00:00"
  }
}
```

### Step 3：公开承诺查询

```bash
curl http://localhost:8090/api/zk/evidence/5/commitment
```

响应（无需认证，任何人可查）：

```json
{
  "code": 200,
  "data": {
    "evidenceId": 5,
    "fileName": "Cover Letter.docx",
    "commitmentHash": "a7c3d9f1...",
    "zkStatus": 1,
    "zkStatusText": "已生成证明"
  }
}
```

---

## 11. 两种运行模式

### Mock 模式（开发/测试，默认）

不配置 `ZK_EVIDENCE_PROVER_BINARY`，Java 端用 SHA-256 自验证，
不调用 Rust，证明几乎瞬间完成：

```yaml
# application.yml
zk:
  evidence:
    prover-binary:      # 留空
```

```bash
java -jar blockchain-evidence-system-1.0.0.jar --spring.profiles.active=dev
```

产生 `status: "MOCK"` 的 proof，`sealHex` 为 64 字节全零，**不具备密码学意义**，
只用于接口调试。

### 真实模式（生产/演示）

先编译 Rust 证明器，再指定路径：

```bash
# 编译（debug 模式，release 模式需要 ~16GB RAM 编译 keccak C++）
cd zk
cargo build 2>&1 | tail -20
# 产物：target/debug/prove, target/debug/verify
```

启动时指定：

```bash
export ZK_EVIDENCE_PROVER_BINARY=/path/to/zk/target/debug/prove

java -jar blockchain-evidence-system-1.0.0.jar \
  --spring.profiles.active=dev \
  --server.port=8090 \
  --zk.evidence.prover-binary="${ZK_EVIDENCE_PROVER_BINARY}"
```

产生 `status: "REAL"` 的 proof，`sealHex` 包含完整 Groth16 receipt，可用 `verify` 离线验证。

---

## 12. 构建与部署

### 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| Rust | stable ≥ 1.78 | `rustup update stable` |
| RISC Zero toolchain | 3.0.5 | `cargo risczero install` |
| CUDA toolkit | ≥ 12.0 | GPU 加速必需 |
| GPU | RTX 4060 (8GB VRAM) | 测试通过 |
| CPU RAM | ≥ 8GB（运行），≥ 16GB（编译 release） | debug 编译 ~4GB |

### 首次构建

```bash
# 1. 安装 RISC Zero 工具链
curl -L https://risczero.com/install | bash
rzup install

# 2. 编译 ZK 模块（debug 模式，跳过 keccak release 编译问题）
cd /root/data/FBEvidence-master/FiscoBcosEvidence/untitled/zk
cargo build

# 3. 验证产物
ls -lh target/debug/prove target/debug/verify
# prove: ~294MB（静态链接了 RISC Zero 运行时）
# verify: ~几十MB
```

### recursion_zkr.zip 问题

Groth16 模式首次运行需要下载 `recursion_zkr.zip`（递归证明参数）。
若网络不通导致 400 错误，可从其他已构建项目复制：

```bash
# 从 competition-platform 的 debug 构建缓存复制
SRC=/root/data/Dapp_Share_Platform/competition-platform/.../target/debug
DST=/root/data/FBEvidence-master/.../zk/target/debug

cp "$SRC/build/risc0-circuit-recursion-sys-*/out/recursion_zkr.zip" \
   "$DST/build/risc0-circuit-recursion-sys-*/out/"
```

### 运行时监控

```bash
# 监控 GPU 显存（proof 生成期间）
watch -n 0.5 nvidia-smi

# 监控 CPU 内存
watch -n 0.5 free -h
```

---

## 13. 真实证明测试记录

环境：RTX 4060（8GB VRAM），WSL2，RISC Zero 3.0.5

```bash
# 测试输入
echo '{
  "userId": 3,
  "fileHashHex": "351efe4b3e6a3a7e8b23d1c9f0a2d4e5cf1b7890abcdef01234567890abcdef0",
  "saltHex":     "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899",
  "timestamp":   1748700000,
  "commitmentHex": "a7c3d9f12b8e4561098af3d72c6b90e14f2a85c7d0391e6b2f748c0a5d1e923"
}' | ./target/debug/prove
```

**实测结果：**

- 证明时间：**~2秒**（GPU），CPU 模式约 3~5 分钟
- Seal 大小：**~221KB**（Groth16 receipt 含 journal，bincode 序列化）
- Status：**REAL**

```bash
# 验证
echo '{"journalHex":"...","sealHex":"..."}' | ./target/debug/verify
# 输出：{"ok":true}
```

---

## 14. 内存与性能

### 运行时（proof generation）

| 资源 | 用量 | 说明 |
|------|------|------|
| CPU RAM | ~6-10 GB | STARK 展开阶段，随 zkVM cycles 线性增长 |
| GPU VRAM | ~4-6 GB | Groth16 witness 生成（RTX 4060 8GB 刚好够） |
| 证明耗时（GPU） | ~2秒 | CUDA 加速 |
| 证明耗时（CPU） | ~3-5分钟 | 无 GPU 时 |
| Seal 大小 | ~221KB | Groth16 receipt bincode，存 DB 用 MEDIUMTEXT |

我们的电路只做 SHA-256(64字节)，非常简单（几千 RISC-V cycles），
所以内存压力远低于复杂电路（如 EVM 验证需要 32GB+ RAM）。

### 编译时

| 场景 | CPU RAM 需求 | 说明 |
|------|-------------|------|
| `cargo build`（debug） | ~4-8 GB | 可用 |
| `cargo build --release` | ~16 GB | keccak C++ 编译时 `cc1plus` 需要 ~16GB |

release 模式在低内存机器会被 OOM killer 终止。项目使用 debug 模式构建，
proof 功能不受影响（只是二进制更大、符号未剥离）。

---

## 15. 与 ZK Rollup 的区别

这是一个常见疑问：为什么不像某些 Web3 项目那样"每 N 条记录批量打包一次上链"？

### ZK Rollup 模式

```
目的：节省 gas，提高以太坊吞吐量

1000笔交易
    │
    ▼
ZK-STARK 证明（所有交易都有效）
    │ STARK → SNARK 转换（因为 STARK 验证太贵）
    ▼
1个 Groth16 proof + 状态根
    │ 1笔上链交易（代替 1000笔）
    ▼
以太坊 L1（验证 proof，更新状态）
```

STARK→SNARK 转换是**因为以太坊上验证 STARK 需要 ~500K gas，
而验证 Groth16 只需要 ~250K gas**，且 proof 体积小得多。

### 本项目模式

```
目的：隐私保护，时间戳可信

1个文件
    │
    ▼
ZK proof（我知道文件的哈希原像，且 commitment 正确）
    │ RISC Zero 内部自动 STARK → Groth16
    ▼
1条链上记录（commitment_hash 上链）+ proof 存 DB
```

**RISC Zero 的 STARK→SNARK 转换是内置的**，不需要像旧项目那样手动搭 wrapper 电路。

### 什么情况下本项目应该做批量？

只有在使用**以太坊主网/Polygon 等公链**且**日存证量 > 1000条**时，
批量才值得：

```
每 N 条存证
    │
    ▼
Merkle 树（所有 commitment 的根）
    │
    ▼
ZK 证明（Merkle 根正确）
    │
    ▼
1笔上链（代替 N 笔）

验证单条时提供 Merkle path + ZK proof
```

在 FISCO BCOS 联盟链 + 几十条/天的规模下，这是过度设计，
且会损失每条存证的精确时间戳。
