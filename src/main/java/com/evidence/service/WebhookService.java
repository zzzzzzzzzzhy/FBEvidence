package com.evidence.service;

public interface WebhookService {
    /** 处理 GitHub push payload，返回处理的 commit 数量 */
    int processGithubPush(String payload) throws Exception;
}
