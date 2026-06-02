// ============================================================
// 构建脚本（build.rs）
//
// Rust 项目根目录下的 build.rs 会在编译时自动执行，
// 比正式代码更早运行，用于生成代码、编译外部依赖等。
//
// 这里的作用：
//   把 guest/ 目录下的电路程序编译成 RISC-V ELF，
//   并生成两个常量嵌入到 host 代码里：
//     EVIDENCE_VERIFY_ELF → guest 程序的字节码（二进制内容）
//     EVIDENCE_VERIFY_ID  → guest ELF 的哈希指纹（[u32; 8]）
//
// 这样 prove 二进制就是自包含的，发布时不需要单独发布 guest ELF 文件。
// ============================================================

fn main() {
    // risc0_build::embed_methods() 会：
    //   1. 读取 Cargo.toml 里 [package.metadata.risc0] methods = ["guest"] 的配置
    //   2. 编译 guest/ 目录下的 Rust 程序为 RISC-V 目标（riscv32im-risc0-zkvm-elf）
    //   3. 把编译产物的字节和哈希写入 OUT_DIR/methods.rs
    //   4. methods/src/lib.rs 里的 include! 宏会把这个文件引入，
    //      从而让 host 代码能用 EVIDENCE_VERIFY_ELF 和 EVIDENCE_VERIFY_ID
    risc0_build::embed_methods();
}
