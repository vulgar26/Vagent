package com.vagent.llm.impl;

import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmClient;
import com.vagent.llm.LlmMessage;
import com.vagent.llm.LlmStreamSink;
import com.vagent.llm.config.LlmProperties;

/**
 * 开发/演示用流式实现：将最后一条 {@link LlmMessage.Role#USER} 内容按小块延迟输出，不调用外部模型。
 * <p>
 * 用于验证 SSE、取消与 {@link LlmStreamSink} 契约；与 {@code noop} 并列，通过 {@code vagent.llm.provider=fake-stream} 启用。
 */
public final class FakeStreamingLlmClient implements LlmClient {

    private final LlmProperties llmProperties;

    public FakeStreamingLlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Override
    public void streamChat(LlmChatRequest request, LlmStreamSink sink) {
        String text = "";
        for (int i = request.messages().size() - 1; i >= 0; i--) {
            LlmMessage m = request.messages().get(i);
            if (m.role() == LlmMessage.Role.USER) {
                text = m.content();
                break;
            }
        }
        int chunkSize = Math.max(1, llmProperties.getFakeStreamChunkSize());
        int delayMs = Math.max(0, llmProperties.getFakeStreamChunkDelayMs());
        for (int start = 0; start < text.length(); start += chunkSize) {
            if (request.isCancelled()) {
                sink.onComplete();
                return;
            }
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    sink.onError(e);
                    return;
                }
            }
            if (request.isCancelled()) {
                sink.onComplete();
                return;
            }
            int end = Math.min(start + chunkSize, text.length());
            sink.onChunk(text.substring(start, end));
        }
        sink.onComplete();
    }
}
