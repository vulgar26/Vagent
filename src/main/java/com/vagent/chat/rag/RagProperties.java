package com.vagent.chat.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 编排相关开关与阈值，前缀 {@code vagent.rag}。
 * <p>
 * <b>enabled：</b>
 * 为 true 时，{@link com.vagent.chat.StreamChatService} 将走「鉴权 → 读历史 → 落库用户句 → 向量检索 → 拼 system+历史+当前 user → SSE」整条链路；
 * 为 false 时保持 M3 行为：仅把单条 user 消息交给 LLM，便于对照实验或本地快速验证 SSE。
 * <p>
 * <b>top-k：</b>
 * 传给 {@link com.vagent.kb.KnowledgeRetrieveService#search} 的命中条数上限；越大上下文越长、延迟与费用越高。
 * <p>
 * <b>max-history-messages：</b>
 * 从 {@code messages} 表最多取几条「过去的」USER/ASSISTANT 轮次参与拼 prompt（不含本轮刚插入的用户消息，
 * 因为本轮正文由编排层单独追加）。用于控制长会话下的 token 占用。
 * <p>
 * <b>empty-hits-behavior（U3）：</b>
 * 仅在 {@link com.vagent.orchestration.model.ChatBranch#RAG} 且检索命中数为 0 时生效；
 * {@link EmptyHitsBehavior#NO_LLM} 时不调用 LLM，仅推送 {@link #emptyHitsNoLlmMessage}；{@link EmptyHitsBehavior#ALLOW_LLM} 保持历史行为。
 */
@ConfigurationProperties(prefix = "vagent.rag")
public class RagProperties {

    /**
     * 是否启用 RAG 多轮编排；默认开启以符合里程碑「主链路」预期。
     */
    private boolean enabled = true;

    /**
     * 知识库向量检索 Top-K，≥1。
     */
    private int topK = 5;

    /**
     * 参与拼 LLM 的历史消息条数上限（按时间从早到晚截断最近 N 条）。
     */
    private int maxHistoryMessages = 20;

    /**
     * RAG 分支检索无命中时是否仍调用 LLM；默认 {@link EmptyHitsBehavior#ALLOW_LLM} 兼容旧版。
     */
    private EmptyHitsBehavior emptyHitsBehavior = EmptyHitsBehavior.ALLOW_LLM;

    /**
     * 当 {@link #emptyHitsBehavior} 为 {@link EmptyHitsBehavior#NO_LLM} 时，经 SSE 推送给用户的固定全文（不调 LLM）。
     */
    private String emptyHitsNoLlmMessage =
            "在知识库中未检索到与当前问题相关的片段。请尝试更换关键词或补充描述；若仍无结果，请确认对应文档是否已入库。";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public void setMaxHistoryMessages(int maxHistoryMessages) {
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public EmptyHitsBehavior getEmptyHitsBehavior() {
        return emptyHitsBehavior;
    }

    public void setEmptyHitsBehavior(EmptyHitsBehavior emptyHitsBehavior) {
        this.emptyHitsBehavior = emptyHitsBehavior;
    }

    public String getEmptyHitsNoLlmMessage() {
        return emptyHitsNoLlmMessage;
    }

    public void setEmptyHitsNoLlmMessage(String emptyHitsNoLlmMessage) {
        this.emptyHitsNoLlmMessage = emptyHitsNoLlmMessage;
    }
}
