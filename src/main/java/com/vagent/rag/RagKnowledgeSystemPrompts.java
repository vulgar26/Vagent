package com.vagent.rag;

import com.vagent.kb.dto.RetrieveHit;

import java.util.List;

/**
 * 从检索命中构造「知识库 RAG」SYSTEM 提示词，供 {@link com.vagent.chat.RagStreamChatService} 与
 * {@link com.vagent.eval.EvalChatController}（可选 full-answer）共用，避免两套文案漂移。
 */
public final class RagKnowledgeSystemPrompts {

    private RagKnowledgeSystemPrompts() {}

    public static String buildFromHits(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "你是企业场景下的助手。当前知识库检索未命中相关片段。请结合对话历史（若有）与常识谨慎作答；"
                    + "若无法确认内部事实，请明确说明信息来源不足，不要编造内部政策或文档细节。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你是企业场景下的助手。以下是与用户问题相关的知识库片段（按相似度排序）。请优先依据这些内容组织答案；")
                .append("若用户问题与片段明显无关，可先简要说明再回答。片段内容：\n\n");
        for (int i = 0; i < hits.size(); i++) {
            RetrieveHit h = hits.get(i);
            sb.append("--- 片段 ").append(i + 1);
            if ("global".equals(h.getSource())) {
                sb.append("（来源：共享语料/跨用户召回，仅作参考）");
            }
            sb.append(" ---\n");
            String body = h.getContent();
            sb.append(body != null ? body : "").append("\n\n");
        }
        return sb.toString();
    }
}
