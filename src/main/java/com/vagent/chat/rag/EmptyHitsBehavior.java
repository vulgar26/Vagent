package com.vagent.chat.rag;

/**
 * U3：当 RAG 分支且知识库检索结果为空时，是否仍调用 {@link com.vagent.llm.LlmClient}。
 * <p>
 * 配置键 {@code vagent.rag.empty-hits-behavior}，取值 {@code no-llm} / {@code allow-llm}（与策划书 §3 / DECISIONS 对齐）。
 */
public enum EmptyHitsBehavior {

    /**
     * 不调用大模型：固定文案经 SSE 输出后 {@code done}，与澄清分支一致（不调 LLM）。
     */
    NO_LLM,

    /**
     * 历史默认：仍构造「未命中」说明的 SYSTEM 并调用 LLM（便于依赖常识作答）。
     */
    ALLOW_LLM
}
