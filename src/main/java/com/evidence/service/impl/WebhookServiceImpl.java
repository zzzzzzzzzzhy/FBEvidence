package com.evidence.service.impl;

import com.evidence.common.Constants;
import com.evidence.entity.FileEvidence;
import com.evidence.mapper.EvidenceMapper;
import com.evidence.service.WebhookService;
import com.evidence.util.RedisQueueUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final EvidenceMapper  evidenceMapper;
    private final RedisQueueUtil  redisQueueUtil;
    private final ObjectMapper    objectMapper;

    @Override
    public int processGithubPush(String payload) throws Exception {
        JsonNode root  = objectMapper.readTree(payload);
        JsonNode commits = root.path("commits");

        if (commits.isMissingNode() || commits.isEmpty()) {
            log.info("[Webhook] push 事件中没有 commit，忽略");
            return 0;
        }

        String repoFullName = root.path("repository").path("full_name").asText("unknown/unknown");
        String ref          = root.path("ref").asText("");
        // ref 形如 "refs/heads/main" → "main"
        String branch       = ref.startsWith("refs/heads/") ? ref.substring(11) : ref;
        String pusher       = root.path("pusher").path("name").asText("unknown");

        log.info("[Webhook] 处理 GitHub push: repo={}, branch={}, pusher={}, commits={}",
                repoFullName, branch, pusher, commits.size());

        List<FileEvidence> saved = new ArrayList<>();

        for (JsonNode commit : commits) {
            String commitSha  = commit.path("id").asText();
            String message    = commit.path("message").asText("").replace("\n", " ").trim();
            String author     = commit.path("author").path("name").asText(pusher);
            String email      = commit.path("author").path("email").asText("");
            String timestamp  = commit.path("timestamp").asText("");

            // 用 commit SHA 作为 fileHash（40位hex，SHA-1）
            // 检查是否已存证过
            if (evidenceMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileEvidence>()
                        .eq(FileEvidence::getFileHash, commitSha)) > 0) {
                log.debug("[Webhook] commit {} 已存证，跳过", commitSha);
                continue;
            }

            FileEvidence ev = new FileEvidence();
            ev.setUserId(1L);                          // 系统用户（可后续改为按 GitHub 账号映射）
            ev.setFileName(repoFullName + "@" + commitSha.substring(0, 7));
            ev.setFileHash(commitSha);                 // 40-char commit SHA
            ev.setFileSize((long) commitSha.length());
            ev.setFilePath("github://" + repoFullName + "/commit/" + commitSha);
            ev.setHashAlgorithm("GIT-SHA1");
            ev.setContentType("CODE");
            ev.setDescription(String.format("[%s] %s: %s", branch, author, message));
            ev.setGitGroupName(repoFullName.contains("/") ? repoFullName.split("/")[0] : repoFullName);
            ev.setGitProjectName(repoFullName.contains("/") ? repoFullName.split("/")[1] : repoFullName);
            ev.setGitBranchName(branch);
            ev.setGitCommitHash(commitSha);
            ev.setGitCommitMessage(message);
            ev.setGitAuthorName(author);
            ev.setGitAuthorEmail(email);
            ev.setGitCommitTime(parseTimestamp(timestamp));
            ev.setGitRepositoryPath("github://" + repoFullName);
            ev.setGitStatus(2);                        // 2 = 远程成功（来自 GitHub）
            ev.setChainStatus(Constants.CHAIN_STATUS_PENDING);
            ev.setCreatedAt(LocalDateTime.now());
            ev.setUpdatedAt(LocalDateTime.now());

            evidenceMapper.insert(ev);
            saved.add(ev);

            // 推入异步上链队列
            RedisQueueUtil.BlockchainTask task = new RedisQueueUtil.BlockchainTask(
                ev.getId(), commitSha,
                ev.getFileName(), author,
                ev.getFileSize(), ev.getDescription()
            );
            redisQueueUtil.pushBlockchainTask(task);

            log.info("[Webhook] commit {} 已存证并推入上链队列, evidenceId={}",
                    commitSha.substring(0, 7), ev.getId());
        }

        log.info("[Webhook] 本次 push 处理完成: repo={}, branch={}, 新增存证={}/{}",
                repoFullName, branch, saved.size(), commits.size());
        return saved.size();
    }

    private LocalDateTime parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return LocalDateTime.now();
        try {
            // GitHub 格式: "2024-01-15T10:30:00+08:00"
            return LocalDateTime.parse(ts.substring(0, 19),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
