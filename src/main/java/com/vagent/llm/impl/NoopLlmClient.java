package com.vagent.llm.impl;

import com.vagent.llm.LlmChatRequest;
import com.vagent.llm.LlmClient;
import com.vagent.llm.LlmStreamSink;

/**
 * 「空操作」版 {@link LlmClient}：不调用任何外部大模型，立即结束流。
 * <p>
 * <b>这个类是干什么的：</b>
 * 在尚未选定国内厂商、或本地开发没有 API Key 时，仍能让 Spring 上下文成功创建、集成测试通过；
 * 同时让依赖 {@link LlmClient} 的代码可以编译联调（行为是零输出直接完成）。
 * <p>
 * <b>为什么需要而不是删掉接口：</b>
 * 若删掉接口只留实现，会失去「可替换」的边界；noop 是显式的占位策略，与真实实现并列，通过配置切换。
 * <p>
 * <b>注意：</b>
 * 生产环境若误配为 noop，用户将看不到模型输出；上线前应改为真实 provider 并做好配置校验（可在后续版本增加启动检查）。
 */
public final class NoopLlmClient implements LlmClient {

    @Override
    public void streamChat(LlmChatRequest request, LlmStreamSink sink) {
        // 不调用 onChunk，仅表示「一次空的流式会话已结束」，满足「必须调用 onComplete 或 onError 一次」的约定
        sink.onComplete();
    }
}
