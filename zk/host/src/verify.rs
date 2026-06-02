// ============================================================
// 验证器（Host）：离线验证 ZK proof 是否有效
//
// 这个程序不需要重新执行 guest 电路，也不需要 GPU，
// 只需要几毫秒就能验证一个 proof 是否真实有效。
//
// 使用场景：
//   - 任何人拿到 (journalHex, sealHex) 都可以独立验证
//   - 不依赖中心服务，纯数学验证
//   - 验证通过 = 确认存证者确实在当时持有对应文件
//
// 调用方式：
//   echo '{"journalHex":"...","sealHex":"..."}' | ./verify
//   成功：输出 {"ok":true}，退出码 0
//   失败：输出错误信息，退出码非零
// ============================================================

// bail! 宏：直接返回一个错误（类似 throw new Exception(...)）
// Context trait：在错误上附加说明信息
use anyhow::{bail, Context, Result};

// 引入 guest 程序的"指纹"（ELF 内容的哈希）。
// 验证时用这个 ID 确认 proof 是由正确版本的电路生成的，
// 防止有人用不同的电路伪造 proof。
use methods::EVIDENCE_VERIFY_ID;

// Receipt 是 RISC Zero proof 的完整载体，包含：
//   receipt.journal → 公开输出（user_id, timestamp, commitment）
//   receipt 内部    → Groth16 proof 数据
use risc0_zkvm::Receipt;

use serde::Deserialize;
use std::io::{self, Read};

// ── 输入格式（stdin JSON）─────────────────────────────────────────

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")] // JSON 驼峰 → Rust 下划线
struct VerifyJob {
    // journal 字节的十六进制编码，来自 prove 的输出或数据库的 journal_hex 字段
    journal_hex: String,

    // 整个 receipt 的 bincode 序列化再 hex 编码，来自数据库的 seal_hex 字段
    seal_hex: String,
}

// ── 主程序 ───────────────────────────────────────────────────────

fn main() -> Result<()> {
    // 从 stdin 读取全部内容
    let mut buf = String::new();
    io::stdin().read_to_string(&mut buf)?;

    // 解析 JSON 输入
    let job: VerifyJob = serde_json::from_str(&buf).context("parse input JSON")?;

    // 把 journal_hex 解码成字节数组
    let journal_bytes = hex::decode(&job.journal_hex).context("decode journalHex")?;

    // 把 seal_hex 解码成字节数组，再用 bincode 反序列化成 Receipt 结构体。
    // bincode 是 prove.rs 序列化时用的格式，这里对称解码。
    let receipt_bytes = hex::decode(&job.seal_hex).context("decode sealHex")?;
    let receipt: Receipt = bincode::deserialize(&receipt_bytes)
        .context("deserialize receipt")?;

    // 第一步验证：检查传入的 journal_hex 和 receipt 内部存储的 journal 是否一致。
    // 防止有人把 journal 替换成伪造的内容，同时拿着原始 proof。
    // （proof 和 journal 是绑定的，不能单独修改 journal）
    if receipt.journal.bytes != journal_bytes {
        bail!("journal mismatch: journalHex does not match receipt journal");
    }

    // 第二步验证：核心步骤。
    // receipt.verify(IMAGE_ID) 做了以下检查：
    //   1. Groth16 proof 数学验证（proof 本身是否合法）
    //   2. IMAGE_ID 匹配（proof 确实由 EVIDENCE_VERIFY 这个电路生成，不是其他电路）
    //   3. journal 完整性（公开输出未被篡改）
    //
    // 这三项任何一项失败都会返回错误。
    // 成功意味着：存证者确实在某个时刻执行了正确的 SHA-256 承诺计算，
    // 且结果与链上记录一致——这是数学保证，不依赖任何信任。
    receipt.verify(EVIDENCE_VERIFY_ID)
           .context("ZK proof verification failed")?;

    // 全部验证通过，输出成功标志。退出码 0。
    println!("{{\"ok\":true}}");
    Ok(())
}
