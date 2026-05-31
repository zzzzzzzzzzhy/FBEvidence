package com.evidence.controller;

import com.evidence.common.Result;
import com.evidence.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Formatter;

@Slf4j
@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @Value("${webhook.github.secret:}")
    private String webhookSecret;

    /**
     * 接收 GitHub push 事件，自动将每个 commit SHA 存证到 FISCO BCOS。
     *
     * 配置方式：在 GitHub 仓库 Settings → Webhooks 添加：
     *   Payload URL: http://your-server/webhook/github
     *   Content type: application/json
     *   Secret: （与 webhook.github.secret 一致）
     *   Events: Just the push event
     */
    @PostMapping("/github")
    public Result<String> githubPush(
            @RequestBody String payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String event,
            HttpServletRequest request) {

        log.info("[Webhook] GitHub event={}, signature={}", event, signature != null ? "present" : "absent");

        // 验签（配置了 secret 才校验）
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signature == null || !verifySignature(payload, signature)) {
                log.warn("[Webhook] Signature verification failed");
                return Result.error("签名验证失败");
            }
        }

        // 只处理 push 事件
        if (!"push".equals(event)) {
            return Result.success("忽略非 push 事件: " + event);
        }

        try {
            int count = webhookService.processGithubPush(payload);
            return Result.success("存证成功，共处理 " + count + " 个 commit");
        } catch (Exception e) {
            log.error("[Webhook] 处理 GitHub push 失败", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    private boolean verifySignature(String payload, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + toHex(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            log.error("[Webhook] 签名计算失败", e);
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        Formatter fmt = new Formatter();
        for (byte b : bytes) fmt.format("%02x", b);
        return fmt.toString();
    }
}
