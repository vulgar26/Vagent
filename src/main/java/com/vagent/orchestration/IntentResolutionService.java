package com.vagent.orchestration;

import com.vagent.orchestration.model.IntentResult;

/**
 * 意图判定：决定本轮走 RAG、不经检索的寒暄、或仅输出澄清引导。
 */
public interface IntentResolutionService {

    IntentResult resolve(String currentUserMessage);
}
