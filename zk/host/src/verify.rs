//! Host verifier for file evidence commitment proofs.
//!
//! # Input (stdin JSON)
//! ```json
//! {
//!   "journalHex": "...",
//!   "sealHex": "..."
//! }
//! ```
//! Exits 0 on success, non-zero on failure.

use anyhow::{bail, Context, Result};
use methods::EVIDENCE_VERIFY_ID;
use risc0_zkvm::Receipt;
use serde::Deserialize;
use std::io::{self, Read};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct VerifyJob {
    journal_hex: String,
    seal_hex:    String,
}

fn main() -> Result<()> {
    let mut buf = String::new();
    io::stdin().read_to_string(&mut buf)?;
    let job: VerifyJob = serde_json::from_str(&buf).context("parse input JSON")?;

    let journal_bytes = hex::decode(&job.journal_hex).context("decode journalHex")?;
    let receipt_bytes = hex::decode(&job.seal_hex).context("decode sealHex")?;

    let receipt: Receipt = bincode::deserialize(&receipt_bytes).context("deserialize receipt")?;

    if receipt.journal.bytes != journal_bytes {
        bail!("journal mismatch: journalHex does not match receipt journal");
    }

    receipt.verify(EVIDENCE_VERIFY_ID).context("ZK proof verification failed")?;

    println!("{{\"ok\":true}}");
    Ok(())
}
