//! Host prover for file evidence commitment.
//!
//! # Input (stdin JSON)
//! ```json
//! {
//!   "userId": 3,
//!   "fileHashHex": "351efe...",
//!   "saltHex": "aabbcc...",
//!   "timestamp": 1748700000,
//!   "commitmentHex": "deadbeef..."
//! }
//! ```
//!
//! # Output (stdout JSON)
//! ```json
//! {
//!   "imageId": "0x...",
//!   "journalHex": "...",
//!   "sealHex": "..."
//! }
//! ```

use anyhow::{Context, Result};
use methods::{EVIDENCE_VERIFY_ELF, EVIDENCE_VERIFY_ID};
use risc0_zkvm::{default_prover, ExecutorEnv};
use serde::{Deserialize, Serialize};
use std::io::{self, Read};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProveJob {
    user_id:        u64,
    file_hash_hex:  String,
    salt_hex:       String,
    timestamp:      u64,
    commitment_hex: String,
}

#[derive(Serialize)]
struct GuestInput {
    file_hash:           [u8; 32],
    salt:                [u8; 32],
    user_id:             u64,
    timestamp:           u64,
    expected_commitment: [u8; 32],
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ProveResult {
    image_id:    String,
    journal_hex: String,
    seal_hex:    String,
}

fn main() -> Result<()> {
    let mut stdin_buf = String::new();
    io::stdin().read_to_string(&mut stdin_buf)?;
    let job: ProveJob = serde_json::from_str(&stdin_buf).context("parse input JSON")?;

    let file_hash  = hex_to_32(&job.file_hash_hex,  "fileHashHex")?;
    let salt       = hex_to_32(&job.salt_hex,        "saltHex")?;
    let commitment = hex_to_32(&job.commitment_hex,  "commitmentHex")?;

    let guest_input = GuestInput {
        file_hash,
        salt,
        user_id:             job.user_id,
        timestamp:           job.timestamp,
        expected_commitment: commitment,
    };

    let env = ExecutorEnv::builder()
        .write(&guest_input)
        .context("write guest input")?
        .build()
        .context("build executor env")?;

    let prover  = default_prover();
    let receipt = prover.prove(env, EVIDENCE_VERIFY_ELF)
        .context("prove")?.receipt;

    let image_id_str = EVIDENCE_VERIFY_ID
        .iter()
        .map(|w| format!("{:08x}", w))
        .collect::<String>();

    let result = ProveResult {
        image_id:    format!("0x{}", image_id_str),
        journal_hex: hex::encode(&receipt.journal.bytes),
        seal_hex:    hex::encode(bincode::serialize(&receipt).context("serialize receipt")?),
    };

    println!("{}", serde_json::to_string(&result)?);
    Ok(())
}

fn hex_to_32(s: &str, field: &str) -> Result<[u8; 32]> {
    let bytes = hex::decode(s).with_context(|| format!("decode hex field {}", field))?;
    bytes.try_into().map_err(|_| anyhow::anyhow!("{} must be 32 bytes (64 hex chars)", field))
}
