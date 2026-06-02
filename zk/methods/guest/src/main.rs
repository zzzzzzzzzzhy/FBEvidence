// ============================================================
// ZK 电路：文件存证承诺验证
// 这个文件运行在 RISC Zero 的 zkVM 虚拟机"里面"，
// 相当于电路逻辑。RISC Zero 会把这段代码编译成 RISC-V 指令，
// 然后在 zkVM 中执行，同时生成"这段程序确实跑了"的证明。
// ============================================================

// 告诉编译器这个程序没有标准的 main 入口（不是普通可执行文件）。
// zkVM 用自己的方式启动程序，不走操作系统的 main()。
#![no_main]

// 引入 zkVM guest 端的工具库：
//   env       → 用来读取 host 传来的私密输入、写出公开输出
//   sha::Impl → zkVM 内置的 SHA-256 实现（在 zkVM 里执行会生成对应的约束）
//   sha::Sha256, Digest → SHA-256 的 trait 和返回值类型
use risc0_zkvm::guest::env;
use risc0_zkvm::sha::{Impl, Sha256, Digest};

// serde 是 Rust 的序列化/反序列化框架。
// Deserialize → 可以从二进制/JSON 反序列化（用于读取 host 传来的输入）
// Serialize   → 可以序列化成二进制/JSON（用于写出公开输出）
use serde::{Deserialize, Serialize};

// 声明 zkVM 程序的入口函数是下面的 main()。
// 这是 RISC Zero 的宏，作用类似于普通程序的 fn main()，
// 但会被 zkVM 运行时正确地调用。
risc0_zkvm::guest::entry!(main);

// ── 输入结构体（私密，只有证明者持有，外部看不到）──────────────────

#[derive(Deserialize)] // 让这个结构体能从 host 传来的二进制数据反序列化
struct EvidenceInput {
    // 文件内容的 SHA-256 哈希，32 字节。
    // 这是"文件持有权"的核心证据，但不能直接上链（上链等于公开文件指纹）。
    file_hash: [u8; 32],

    // 随机盐，32 字节，每次 commit 时由服务端随机生成。
    // 作用：同一个文件每次生成不同的 commitment，防止通过 commitment 反查文件。
    salt: [u8; 32],

    // 文件所有者的用户 ID，写入公开输出，用于证明"谁"拥有这个文件。
    user_id: u64,

    // 存证时间戳（Unix 秒），写入公开输出，用于证明"何时"持有。
    timestamp: u64,

    // 链上已存储的承诺值（commitment_hash）。
    // 电路会重新计算 SHA-256(file_hash ‖ salt)，
    // 然后断言结果 == 这个值，确保 proof 和链上记录一致。
    expected_commitment: [u8; 32],
}

// ── 输出结构体（公开，任何人可读）───────────────────────────────────

#[derive(Serialize)] // 让这个结构体能被序列化写入 journal
struct EvidenceJournal {
    // 用户 ID：证明"谁"拥有文件
    user_id: u64,

    // 时间戳：证明"何时"持有
    timestamp: u64,

    // 承诺值：与链上存储的 commitment_hash 完全一致。
    // 这是唯一公开的"文件相关信息"，不暴露文件内容或哈希。
    commitment: [u8; 32],
}

// ── 主逻辑 ──────────────────────────────────────────────────────────

fn main() {
    // 从 host 读取私密输入。
    // host（prove.rs）在调用 zkVM 前把 EvidenceInput 序列化写进去，
    // 这里反序列化取出来。这个数据只有 zkVM 内部能看到，外部无法获取。
    let input: EvidenceInput = env::read();

    // 构造 SHA-256 的输入：把 file_hash（32字节）和 salt（32字节）拼在一起，
    // 得到 64 字节的 preimage（原像）。
    // ‖ 符号在密码学里表示字节拼接。
    let mut preimage = [0u8; 64];                         // 分配 64 字节全零数组
    preimage[..32].copy_from_slice(&input.file_hash);     // 前 32 字节放 file_hash
    preimage[32..].copy_from_slice(&input.salt);          // 后 32 字节放 salt

    // 用 RISC Zero 内置的 SHA-256 计算哈希。
    // 注意：这不是普通的 SHA-256 库调用。
    // 在 zkVM 里，每一步计算都会被转化为约束（constraint）。
    // 这行代码执行后，zkVM 就"知道"了这个 SHA-256 是怎么算出来的，
    // 并把这个过程编进了最终的 proof 里。
    let digest: Digest = *Impl::hash_bytes(&preimage);

    // 把 Digest 类型（RISC Zero 内部的 32 字节哈希包装类型）
    // 转换成普通的 [u8; 32] 字节数组，方便后续比较。
    let commitment: [u8; 32] = digest.as_bytes().try_into().expect("32 bytes");

    // ── 核心断言 ──────────────────────────────────────────────────────
    // 断言我们刚算出的 commitment 和链上存储的 expected_commitment 完全一致。
    //
    // 这在 zkVM 里不是普通的 panic：
    //   - 如果相等 → 程序继续，proof 生成成功
    //   - 如果不等 → proof 生成失败（不是运行时错误，而是无法产生有效的 proof）
    //
    // 这就是 ZK 的核心价值：你无法伪造一个"通过了这个 assert_eq" 的 proof，
    // 除非你真的知道满足条件的 file_hash 和 salt。
    assert_eq!(commitment, input.expected_commitment, "commitment mismatch");

    // 把公开输出写入 journal（可以理解为 ZK proof 的"声明书"）。
    // journal 是 proof 的一部分，任何人拿到 proof 后都可以读取这里的值，
    // 但无法修改（proof 的数学性质保证了 journal 不可篡改）。
    //
    // 注意：file_hash 和 salt 没有写进 journal，它们永远是私密的。
    // 外界只能看到 commitment，无法反推原始文件。
    env::commit(&EvidenceJournal {
        user_id: input.user_id,
        timestamp: input.timestamp,
        commitment, // 与链上一致的承诺值
    });
}
