package com.vagent.orchestration;

import com.vagent.chat.message.Message;
import com.vagent.orchestration.model.RewriteResult;

import java.util.List;

/**
 * 检索前改写：输出用于 {@link com.vagent.kb.KnowledgeRetrieveService#search} 的 query 文本。
 */
public interface QueryRewriteService {

    RewriteResult rewriteForRetrieval(String currentUserMessage, List<Message> historyPriorToCurrent);
}
