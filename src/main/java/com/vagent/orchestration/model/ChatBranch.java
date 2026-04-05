package com.vagent.orchestration.model;

/**
 * 编排分支：与策划书 §3 中「系统意图直出 / 澄清提前返回 / RAG 主路径」对应的最小枚举。
 * <p>
 * 写入 SSE {@code meta.branch} 时使用 {@link #name()}，便于前端按字符串分支处理。
 */
public enum ChatBranch {
    /** 走知识检索 +（可选）多轮，与 M4 默认行为一致 */
    RAG,

    /**
     * 不经向量检索：仅用简短系统提示 + 历史 + 用户句调用 LLM，适合寒暄类输入。
     */
    SYSTEM_DIALOG,

    /**
     * 不检索、不调主 LLM：仅流式输出固定引导文案后结束（指代过短、信息不足等）。
     */
    CLARIFICATION
}
