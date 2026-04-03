package com.vagent.llm;

/**
 * 大语言模型「流式对话」的可插拔入口接口（与具体厂商解耦）。
 * <p>
 * <b>这个文件是干什么的：</b>
 * 定义「向模型发一次多轮消息、以流的方式收回文本」的统一契约。以后无论接国内哪家（通义、文心、智谱、DeepSeek 等），
 * 业务编排层（RAG、SSE）只依赖本接口，不直接依赖某厂商的 SDK 或 HTTP 细节。
 * <p>
 * <b>为什么用接口而不是直接写某家客户端：</b>
 * LLM 可替换、不要绑定过深。接口 + Spring Bean 按配置切换实现类，换厂商时新增实现类并改配置即可，
 * 不必改编排主链路代码。
 * <p>
 */
public interface LlmClient {

    /**
     * 发起一次流式补全：模型可能多次回调 {@link LlmStreamSink#onChunk(String)}，结束时必须二选一：
     * <ul>
     *   <li>正常结束：调用一次 {@link LlmStreamSink#onComplete()}</li>
     *   <li>失败：调用一次 {@link LlmStreamSink#onError(Throwable)}</li>
     * </ul>
     * 不要既不调完成也不调错误，也不要重复调用完成/错误（避免 SSE 客户端状态错乱）。
     *
     * @param request 本轮请求（消息列表、模型名等）
     * @param sink    流式结果消费者，由调用方（例如 SSE 控制器）传入，用于把 token 写回前端
     */
    void streamChat(LlmChatRequest request, LlmStreamSink sink);
}
