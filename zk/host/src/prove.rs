// ============================================================
// 证明器（Host）：驱动 zkVM 生成 ZK proof
//
// 这个程序运行在"普通环境"（你的 CPU/GPU），
// 负责：
//   1. 从 stdin 读取 JSON 输入（由 Java 服务调用时传入）
//   2. 把私密数据喂给 zkVM，让它执行 guest 电路（main.rs）
//   3. 拿到 Groth16 proof（receipt），序列化后输出到 stdout
//   4. Java 服务读取 stdout，把 proof 存入数据库
//
// 调用方式（Java 端通过 ProcessBuilder 调用）：
//   echo '<JSON>' | ./prove
// ============================================================

// anyhow 是 Rust 常用的错误处理库，
// Context trait 让你能在错误上附加说明信息（类似 Java 的 cause）。
use anyhow::{Context, Result};

// 引入编译时嵌入的 guest 程序：
//   EVIDENCE_VERIFY_ELF → guest 程序的 RISC-V 字节码（二进制）
//   EVIDENCE_VERIFY_ID  → guest 程序的"指纹"，是 ELF 内容的哈希，
//                         用于验证 proof 时确认用的是正确的电路版本
// 这两个常量在编译时由 methods/build.rs 自动生成并嵌入。
use methods::{EVIDENCE_VERIFY_ELF, EVIDENCE_VERIFY_ID};

// default_prover → 自动选择最优证明器（有 CUDA GPU 就用 GPU，否则用 CPU）
// ExecutorEnv   → 执行环境构建器，用来把私密输入传给 guest
use risc0_zkvm::{default_prover, ExecutorEnv};

// serde 序列化/反序列化，Deserialize 用于解析 stdin JSON，Serialize 用于输出结果
use serde::{Deserialize, Serialize};

// 标准库：io::Read trait 让 stdin 可以读成 String
use std::io::{self, Read};

// ── Java 端传入的输入格式（stdin JSON）────────────────────────────

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")] // JSON 字段用驼峰命名（Java 风格），Rust 字段用下划线
struct ProveJob {
    // 文件所有者用户 ID
    user_id: u64,

    // 文件哈希的十六进制字符串（64个十六进制字符 = 32字节）
    // 例："351efe4b3e6a3a7e8b23d1c9..."
    file_hash_hex: String,

    // 随机盐的十六进制字符串（64个十六进制字符 = 32字节）
    salt_hex: String,

    // 存证时间戳（Unix 秒）
    timestamp: u64,

    // 链上已存储的承诺值的十六进制字符串
    commitment_hex: String,
}

// ── 传给 guest（zkVM）的私密输入格式 ──────────────────────────────

#[derive(Serialize)] // 需要序列化后才能传给 zkVM
struct GuestInput {
    file_hash:           [u8; 32],
    salt:                [u8; 32],
    user_id:             u64,
    timestamp:           u64,
    expected_commitment: [u8; 32],
}

// ── 输出到 stdout 的 JSON 格式（Java 端读取）─────────────────────

#[derive(Serialize)]
#[serde(rename_all = "camelCase")] // 输出也用驼峰命名，方便 Java 的 ObjectMapper 解析
struct ProveResult {
    // ELF 指纹（电路版本标识），格式 "0x3f9a2b1c..."
    // 验证时必须用同一个 image_id，保证 proof 和电路版本对应
    image_id: String,

    // journal 字节的十六进制编码。
    // journal 是 guest 通过 env::commit() 写出的公开输出，
    // 包含 user_id、timestamp、commitment。
    journal_hex: String,

    // Groth16 proof 的 bincode 序列化再 hex 编码。
    // 这是整个 receipt（含 journal + proof），存入数据库的 seal_hex 字段。
    // 大小约 221KB（远大于纯 Groth16 proof 的 256 字节，因为包含了 journal 等元数据）。
    seal_hex: String,
}

// ── 主程序 ───────────────────────────────────────────────────────

fn main() -> Result<()> {
    // 从 stdin 读取全部内容到字符串。
    // Java 端用 proc.getOutputStream().write(json) 写入，这里读取。
    let mut stdin_buf = String::new();
    io::stdin().read_to_string(&mut stdin_buf)?;

    // 把 JSON 字符串解析成 ProveJob 结构体。
    // context() 在解析失败时附加说明信息，方便调试。
    let job: ProveJob = serde_json::from_str(&stdin_buf).context("parse input JSON")?;

    // 把十六进制字符串解码成 [u8; 32] 字节数组。
    // hex_to_32 定义在下方，失败时抛出带字段名的错误。
    let file_hash  = hex_to_32(&job.file_hash_hex,  "fileHashHex")?;
    let salt       = hex_to_32(&job.salt_hex,        "saltHex")?;
    let commitment = hex_to_32(&job.commitment_hex,  "commitmentHex")?;

    // 构造传给 guest 的私密输入结构体
    let guest_input = GuestInput {
        file_hash,
        salt,
        user_id:             job.user_id,
        timestamp:           job.timestamp,
        expected_commitment: commitment,
    };

    // 构建 zkVM 执行环境：
    //   .write(&guest_input) → 把私密输入序列化后放入 zkVM 的"输入通道"，
    //                          guest 里的 env::read() 就是从这里读取的。
    //   .build()             → 完成环境构建
    let env = ExecutorEnv::builder()
        .write(&guest_input)
        .context("write guest input")?
        .build()
        .context("build executor env")?;

    // 获取默认证明器。
    // 如果系统有 NVIDIA GPU 且编译时加了 `features = ["cuda"]`，
    // 自动使用 GPU 加速（RTX 4060 约 2 秒）。
    // 否则使用纯 CPU 证明器（约 3~5 分钟）。
    let prover = default_prover();

    // 核心步骤：执行 guest 程序并生成 proof。
    // EVIDENCE_VERIFY_ELF 是编译时嵌入的 guest RISC-V 字节码。
    // 返回的 receipt 包含：
    //   receipt.journal → guest 通过 env::commit() 写出的公开输出
    //   receipt 内部    → Groth16 proof（数学证明）
    let receipt = prover
        .prove(env, EVIDENCE_VERIFY_ELF)
        .context("prove")?
        .receipt;

    // 把 EVIDENCE_VERIFY_ID（[u32; 8] 类型）格式化成十六进制字符串。
    // 每个 u32 格式化为 8 位十六进制，8 个 u32 拼在一起共 64 个字符。
    // 加上 "0x" 前缀。
    let image_id_str = EVIDENCE_VERIFY_ID
        .iter()
        .map(|w| format!("{:08x}", w)) // 每个 u32 → 8位十六进制，不足前补0
        .collect::<String>();

    // 构造输出 JSON：
    //   journal.bytes → guest env::commit() 写出的字节，直接 hex 编码
    //   bincode::serialize(&receipt) → 把整个 receipt（proof + journal）
    //                                  用 bincode 二进制序列化，再 hex 编码
    //   bincode 是一种紧凑的二进制序列化格式，比 JSON 小得多
    let result = ProveResult {
        image_id:    format!("0x{}", image_id_str),
        journal_hex: hex::encode(&receipt.journal.bytes),
        seal_hex:    hex::encode(bincode::serialize(&receipt).context("serialize receipt")?),
    };

    // 把结果输出到 stdout，Java 端通过 proc.getInputStream() 读取
    println!("{}", serde_json::to_string(&result)?);
    Ok(())
}

// ── 辅助函数 ─────────────────────────────────────────────────────

// 把十六进制字符串解码成精确 32 字节的数组。
// 失败原因可能是：hex 字符非法、长度不是 64 字符（32字节）。
// field 参数用于错误信息，告诉调用者是哪个字段出了问题。
fn hex_to_32(s: &str, field: &str) -> Result<[u8; 32]> {
    // hex::decode 把十六进制字符串解码成 Vec<u8>
    let bytes = hex::decode(s)
        .with_context(|| format!("decode hex field {}", field))?;

    // try_into() 尝试把 Vec<u8> 转换成 [u8; 32]，
    // 如果长度不是 32 则失败，返回清晰的错误信息
    bytes.try_into()
         .map_err(|_| anyhow::anyhow!("{} must be 32 bytes (64 hex chars)", field))
}
