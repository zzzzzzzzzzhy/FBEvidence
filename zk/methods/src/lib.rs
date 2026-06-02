// ============================================================
// 自动生成的胶水代码入口
//
// include! 宏在编译时把 build.rs 生成的 methods.rs 文件内容
// 直接嵌入到这里，效果等同于把那个文件的代码复制粘贴过来。
//
// OUT_DIR 是 Cargo 提供的环境变量，指向编译时的临时输出目录。
// methods.rs 里定义了：
//   pub const EVIDENCE_VERIFY_ELF: &[u8] = &[...];  // guest 字节码
//   pub const EVIDENCE_VERIFY_ID: [u32; 8] = [...];  // guest 指纹
//
// host 代码里 `use methods::{EVIDENCE_VERIFY_ELF, EVIDENCE_VERIFY_ID}`
// 引用的就是这里暴露出来的两个常量。
// ============================================================
include!(concat!(env!("OUT_DIR"), "/methods.rs"));
