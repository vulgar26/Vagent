package com.vagent.llm;

/**
 * 流式结果的「消费者」回调：由 {@link LlmClient#streamChat(LlmChatRequest, LlmStreamSink)} 在生成过程中反向调用。
 * <p>
 * <b>这个接口是干什么的：</b>
 * 把「模型吐出的增量文本」和「结束/失败」事件从 LLM 实现层传回调用方（例如后面要写 SSE 时，在 onChunk 里写 event）。
 * 采用回调而不是 {@code Iterator} 或阻塞流，是为了贴合异步流式 IO 与取消（taskId / stop）。
 * <p>
 * <b>为什么不用 Reactor Flux 等：</b>
 * 先保持核心接口依赖最少；若某层希望响应式，可在适配层把回调桥接到 Flux。编排层仍以接口为主更易替换厂商。
 * <p>
 * <b>约定：</b>
 * 一次 {@code streamChat} 调用结束后，必须恰好调用一次 {@link #onComplete()} 或一次 {@link #onError(Throwable)}。
 */
public interface LlmStreamSink {

    /**
     * 收到一段增量文本（可能多次调用；单次可为空串，视实现而定）。
     *
     * @param text 本段文本片段
     */
    void onChunk(String text);

    /** 流正常结束，无更多数据。 */
    void onComplete();

    /**
     * 流因异常结束（网络错误、鉴权失败、厂商返回错误等）。
     *
     * @param t 失败原因
     */
    void onError(Throwable t);
}
