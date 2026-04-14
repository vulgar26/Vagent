package com.vagent.eval;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 评测请求里 {@code X-Eval-Target-Id} 与知识库租户 {@code user_id} 的<strong>稳定映射</strong>（与 {@link EvalChatController} 检索一致）。
 * <p>
 * vagent-eval 拉评测时带 {@code target_id}（如 {@value #DEFAULT_TARGET_ID}）；Vagent 用同一算法得到 {@link UUID}，
 * 再经 {@link com.vagent.user.UserIdFormats#canonical(UUID)} 参与 KB 检索。<strong>向 KB 灌文档时，文档归属必须与该 UUID 一致</strong>，
 * 否则评测侧长期 {@code sources_count=0}。注册普通用户得到的 JWT 与这里<strong>不是</strong>同一用户，不可混用。
 */
public final class EvalStableUserId {

    /** 与常见 eval 配置里 {@code target_id=vagent} 对应。 */
    public static final String DEFAULT_TARGET_ID = "vagent";

    private EvalStableUserId() {
    }

    /**
     * 与 {@code EvalChatController} 内检索租户一致：{@code eval-user|target=<trim(X-Eval-Target-Id)>} 的字节做 {@link UUID#nameUUIDFromBytes(byte[])}。
     *
     * @param xEvalTargetId 请求头 {@code X-Eval-Target-Id}；空则视为空串
     */
    public static UUID fromEvalTargetId(String xEvalTargetId) {
        String t = xEvalTargetId != null ? xEvalTargetId.trim() : "";
        String seed = "eval-user|target=" + t;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    /** 等价于 {@link #fromEvalTargetId(String) fromEvalTargetId}({@value #DEFAULT_TARGET_ID})。 */
    public static UUID forDefaultVagentTarget() {
        return fromEvalTargetId(DEFAULT_TARGET_ID);
    }
}
