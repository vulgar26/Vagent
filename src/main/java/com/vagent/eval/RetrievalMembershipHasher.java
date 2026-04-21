package com.vagent.eval;

import com.vagent.kb.dto.RetrieveHit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 评测 E7：对候选命中 id 做 HMAC 列表，供 vagent-eval 做 membership 校验；算法与请求头材料见 {@code plans/eval-upgrade.md}。
 * <p>
 * SSE 主对话使用<strong>独立</strong>的 k_case 材料（{@link #buildSseHitIdHashes}），与 {@code X-Eval-Token} 派生密钥<strong>不可互换</strong>。
 */
public final class RetrievalMembershipHasher {

    /** 与评测接口历史常量一致：单请求候选哈希条数硬上限（50）。 */
    public static final int ENGINE_MEMBERSHIP_CAP = 50;

    private RetrievalMembershipHasher() {}

    /**
     * 与 {@code sources[].id} / 根级 {@code retrieval_hits[]} 对齐的稳定 chunk / document id。
     */
    public static String canonicalHitId(RetrieveHit h) {
        if (h == null) {
            return "";
        }
        String cid = h.getChunkId() != null ? h.getChunkId().trim() : "";
        if (!cid.isEmpty()) {
            return cid;
        }
        String doc = h.getDocumentId() != null ? h.getDocumentId().trim() : "";
        return !doc.isEmpty() ? doc : "";
    }

    /**
     * 评测路径：材料为 {@code X-Eval-Token} + target/dataset/case；与 vagent-eval 一致。
     */
    public static List<String> buildEvalHitIdHashes(
            String xEvalToken,
            String xEvalTargetId,
            String xEvalDatasetId,
            String xEvalCaseId,
            List<String> canonicalHitIds) {
        if (canonicalHitIds == null || canonicalHitIds.isEmpty()) {
            return List.of();
        }
        String token = xEvalToken != null ? xEvalToken.trim() : "";
        if (token.isEmpty()) {
            return List.of();
        }
        String targetId = xEvalTargetId != null ? xEvalTargetId.trim() : "";
        String datasetId = xEvalDatasetId != null ? xEvalDatasetId.trim() : "";
        String caseId = xEvalCaseId != null ? xEvalCaseId.trim() : "";
        byte[] kCase =
                hmacSha256(
                        token.getBytes(StandardCharsets.UTF_8),
                        ("hitid-key/v1|" + targetId + "|" + datasetId + "|" + caseId)
                                .getBytes(StandardCharsets.UTF_8));
        return hashIdsWithKCase(kCase, canonicalHitIds);
    }

    /**
     * 用户 SSE：k_case 由部署配置的 UTF-8 密钥 + user + conversation + taskId 派生；未配置密钥时调用方应传空列表、不写非空哈希。
     */
    public static List<String> buildSseHitIdHashes(
            String sseMasterSecretUtf8,
            UUID userId,
            String conversationId,
            String taskId,
            List<String> canonicalHitIds) {
        if (canonicalHitIds == null || canonicalHitIds.isEmpty()) {
            return List.of();
        }
        if (sseMasterSecretUtf8 == null || sseMasterSecretUtf8.isEmpty()) {
            return List.of();
        }
        if (userId == null
                || conversationId == null
                || conversationId.isBlank()
                || taskId == null
                || taskId.isBlank()) {
            return List.of();
        }
        String ctx =
                "sse-hitid-key/v1|"
                        + userId
                        + "|"
                        + conversationId.trim()
                        + "|"
                        + taskId.trim();
        byte[] kCase =
                hmacSha256(
                        sseMasterSecretUtf8.getBytes(StandardCharsets.UTF_8),
                        ctx.getBytes(StandardCharsets.UTF_8));
        return hashIdsWithKCase(kCase, canonicalHitIds);
    }

    private static List<String> hashIdsWithKCase(byte[] kCase, List<String> canonicalHitIds) {
        List<String> out = new ArrayList<>(canonicalHitIds.size());
        for (String id : canonicalHitIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            out.add(toHexLower(hmacSha256(kCase, id.getBytes(StandardCharsets.UTF_8))));
        }
        return List.copyOf(out);
    }

    private static byte[] hmacSha256(byte[] key, byte[] msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(msg);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        char[] hex = new char[bytes.length * 2];
        final char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = alphabet[v >>> 4];
            hex[i * 2 + 1] = alphabet[v & 0x0F];
        }
        return new String(hex);
    }
}
