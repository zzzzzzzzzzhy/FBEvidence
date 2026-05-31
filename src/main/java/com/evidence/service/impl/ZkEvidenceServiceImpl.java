package com.evidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evidence.entity.EvidenceZkProof;
import com.evidence.entity.FileEvidence;
import com.evidence.mapper.EvidenceMapper;
import com.evidence.mapper.EvidenceZkProofMapper;
import com.evidence.service.ZkEvidenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZkEvidenceServiceImpl implements ZkEvidenceService {

    private final EvidenceMapper       evidenceMapper;
    private final EvidenceZkProofMapper proofMapper;
    private final ObjectMapper          objectMapper;

    /** Path to compiled `prove` binary. Blank = use mock prover. */
    @Value("${zk.evidence.prover-binary:}")
    private String proverBinary;

    // ── public API ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FileEvidence commitEvidence(Long evidenceId) throws Exception {
        FileEvidence ev = getEvidence(evidenceId);
        if (ev.getCommitmentHash() != null) {
            log.info("[ZK] evidence {} already committed", evidenceId);
            return ev;
        }

        byte[] fileHashBytes = unhex(ev.getFileHash());
        byte[] salt          = new byte[32];
        new SecureRandom().nextBytes(salt);
        byte[] commitment    = sha256Concat(fileHashBytes, salt);

        evidenceMapper.update(null, new LambdaUpdateWrapper<FileEvidence>()
                .eq(FileEvidence::getId, evidenceId)
                .set(FileEvidence::getCommitmentHash, hex(commitment))
                .set(FileEvidence::getSaltHex,        hex(salt)));

        ev.setCommitmentHash(hex(commitment));
        ev.setSaltHex(hex(salt));
        log.info("[ZK] committed evidence {}, commitment={}", evidenceId, hex(commitment));
        return ev;
    }

    @Override
    @Transactional
    public EvidenceZkProof generateProof(Long evidenceId) throws Exception {
        FileEvidence ev = getEvidence(evidenceId);
        if (ev.getCommitmentHash() == null) {
            commitEvidence(evidenceId);
            ev = getEvidence(evidenceId);
        }

        EvidenceZkProof proof;
        if (proverBinary != null && !proverBinary.isBlank()) {
            proof = proveWithBinary(ev);
        } else {
            proof = mockProve(ev);
        }

        proofMapper.insert(proof);
        evidenceMapper.update(null, new LambdaUpdateWrapper<FileEvidence>()
                .eq(FileEvidence::getId, evidenceId)
                .set(FileEvidence::getZkStatus, 1));

        log.info("[ZK] proof generated for evidence {}, status={}", evidenceId, proof.getStatus());
        return proof;
    }

    @Override
    public EvidenceZkProof getProof(Long evidenceId) {
        return proofMapper.findLatestByEvidenceId(evidenceId);
    }

    @Override
    public Map<String, Object> getPublicCommitment(Long evidenceId) {
        FileEvidence ev = getEvidence(evidenceId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("evidenceId",     evidenceId);
        m.put("fileName",       ev.getFileName());
        m.put("commitmentHash", ev.getCommitmentHash());
        m.put("zkStatus",       ev.getZkStatus());
        m.put("zkStatusText",   zkStatusText(ev.getZkStatus()));
        return m;
    }

    // ── proof generation ──────────────────────────────────────────────────

    private EvidenceZkProof mockProve(FileEvidence ev) throws Exception {
        // Verify commitment in Java (mirrors circuit logic)
        byte[] fileHash   = unhex(ev.getFileHash());
        byte[] salt       = unhex(ev.getSaltHex());
        byte[] recomputed = sha256Concat(fileHash, salt);
        if (!Arrays.equals(recomputed, unhex(ev.getCommitmentHash()))) {
            throw new IllegalStateException("Commitment verification failed for evidence " + ev.getId());
        }

        long timestamp = Instant.now().getEpochSecond();
        byte[] journal = encodeJournal(ev.getUserId(), timestamp, unhex(ev.getCommitmentHash()));
        byte[] digest  = sha256(journal);

        EvidenceZkProof proof = new EvidenceZkProof();
        proof.setEvidenceId(ev.getId());
        proof.setUserId(ev.getUserId());
        proof.setImageId("mock-image-id");
        proof.setCommitmentHex(ev.getCommitmentHash());
        proof.setSealHex(hex(new byte[64]));
        proof.setJournalHex(hex(journal));
        proof.setJournalDigest(hex(digest));
        proof.setStatus("MOCK");
        proof.setCreatedAt(LocalDateTime.now());
        return proof;
    }

    private EvidenceZkProof proveWithBinary(FileEvidence ev) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userId",        ev.getUserId());
        input.put("fileHashHex",   ev.getFileHash());
        input.put("saltHex",       ev.getSaltHex());
        input.put("timestamp",     Instant.now().getEpochSecond());
        input.put("commitmentHex", ev.getCommitmentHash());

        String inputJson = objectMapper.writeValueAsString(input);

        Process proc = new ProcessBuilder(proverBinary)
                .redirectErrorStream(true)
                .start();
        try (OutputStream os = proc.getOutputStream()) {
            os.write(inputJson.getBytes());
        }

        String output   = new String(proc.getInputStream().readAllBytes());
        int    exitCode = proc.waitFor();
        if (exitCode != 0) throw new RuntimeException("ZK prover failed: " + output);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(output, Map.class);

        byte[] journalBytes = unhex((String) result.get("journalHex"));
        EvidenceZkProof proof = new EvidenceZkProof();
        proof.setEvidenceId(ev.getId());
        proof.setUserId(ev.getUserId());
        proof.setImageId((String) result.get("imageId"));
        proof.setCommitmentHex(ev.getCommitmentHash());
        proof.setJournalHex((String) result.get("journalHex"));
        proof.setJournalDigest(hex(sha256(journalBytes)));
        proof.setSealHex((String) result.get("sealHex"));
        proof.setStatus("REAL");
        proof.setCreatedAt(LocalDateTime.now());
        return proof;
    }

    // ── journal encoding ─────────────────────────────────────────────────
    // Mirrors risc0 serde: each u8 of commitment stored as LE u32 word (4 bytes)
    // Layout: user_id(8) + timestamp(8) + commitment(32*4=128) = 144 bytes

    private byte[] encodeJournal(Long userId, long timestamp, byte[] commitment) {
        ByteBuffer buf = ByteBuffer.allocate(8 + 8 + 32 * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putLong(userId);
        buf.putLong(timestamp);
        for (byte b : commitment) {
            buf.putInt(Byte.toUnsignedInt(b));
        }
        return buf.array();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private FileEvidence getEvidence(Long id) {
        FileEvidence ev = evidenceMapper.selectById(id);
        if (ev == null) throw new IllegalArgumentException("Evidence not found: " + id);
        return ev;
    }

    private byte[] sha256Concat(byte[] a, byte[] b) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(a);
        md.update(b);
        return md.digest();
    }

    private byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private byte[] unhex(String hex) {
        if (hex == null) return new byte[0];
        int    len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex, i * 2, i * 2 + 2, 16);
        }
        return out;
    }

    private String zkStatusText(Integer status) {
        if (status == null) return "未承诺";
        if (status == 0) return "已承诺，未生成证明";
        if (status == 1) return "已生成证明";
        if (status == 2) return "证明失败";
        return "未知";
    }
}
