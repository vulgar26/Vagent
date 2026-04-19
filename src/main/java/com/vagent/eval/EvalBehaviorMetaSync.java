package com.vagent.eval;

import java.util.Map;

/**
 * 将根级 {@code behavior}/{@code error_code} 同步写入响应 {@code meta}，与 SSE 首帧 {@code type=meta} 使用同一套键名，
 * 便于客户端与 vagent-eval 对照（见 {@code plans/vagent-upgrade.md} §P1-0）。
 */
public final class EvalBehaviorMetaSync {

    private EvalBehaviorMetaSync() {}

    /**
     * @param behavior  与 {@link com.vagent.eval.dto.EvalChatResponse#getBehavior()} 同值；空白则移除 {@code meta.behavior}
     * @param errorCode   与根级 {@code error_code} 同值；{@code null} 或空白则移除 {@code meta.error_code}（等同成功路径根级无归因码）
     */
    public static void applyRootToMeta(Map<String, Object> meta, String behavior, String errorCode) {
        if (behavior != null && !behavior.isBlank()) {
            meta.put("behavior", behavior);
        } else {
            meta.remove("behavior");
        }
        if (errorCode != null && !errorCode.isBlank()) {
            meta.put("error_code", errorCode);
        } else {
            meta.remove("error_code");
        }
    }
}
