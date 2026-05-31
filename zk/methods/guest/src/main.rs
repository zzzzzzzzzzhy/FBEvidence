#![no_main]
//! ZK circuit: proves a file evidence commitment is valid.
//!
//! Proves: SHA-256(file_hash_32 ‖ salt_32) == commitment
//! where file_hash is SHA-256 of the original file content.
//!
//! This lets an owner prove they held a specific file at a given time
//! without revealing the file content — only the commitment is public.

use risc0_zkvm::guest::env;
use risc0_zkvm::sha::{Impl, Sha256, Digest};
use serde::{Deserialize, Serialize};

risc0_zkvm::guest::entry!(main);

#[derive(Deserialize)]
struct EvidenceInput {
    file_hash:  [u8; 32],   // SHA-256 of file content (private)
    salt:       [u8; 32],   // random salt (private)
    user_id:    u64,        // owner
    timestamp:  u64,        // unix seconds at commit time
    expected_commitment: [u8; 32],  // commitment stored on-chain
}

#[derive(Serialize)]
struct EvidenceJournal {
    user_id:    u64,
    timestamp:  u64,
    commitment: [u8; 32],   // SHA-256(file_hash ‖ salt) — public output
}

fn main() {
    let input: EvidenceInput = env::read();

    // Compute SHA-256(file_hash ‖ salt)
    let mut preimage = [0u8; 64];
    preimage[..32].copy_from_slice(&input.file_hash);
    preimage[32..].copy_from_slice(&input.salt);

    let digest: Digest = *Impl::hash_bytes(&preimage);
    let commitment: [u8; 32] = digest.as_bytes().try_into().expect("32 bytes");

    // Assert commitment matches what was stored on-chain
    assert_eq!(commitment, input.expected_commitment, "commitment mismatch");

    env::commit(&EvidenceJournal {
        user_id: input.user_id,
        timestamp: input.timestamp,
        commitment,
    });
}
