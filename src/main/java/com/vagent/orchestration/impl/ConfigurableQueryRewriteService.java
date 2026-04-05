package com.vagent.orchestration.impl;

import com.vagent.chat.message.Message;
import com.vagent.orchestration.OrchestrationProperties;
import com.vagent.orchestration.QueryRewriteService;
import com.vagent.orchestration.model.RewriteResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 {@link OrchestrationProperties#getRewriteStrategy()} 选择透传或拼接历史 USER 句。
 * <p>
 * <b>CONCAT_USER_HISTORY：</b>解决「指代依赖上文」时的检索召回——仅把历史中的 USER 与本轮拼成一段再 embed，
 * 不改变 {@code messages} 表结构，也不改变送给 LLM 的多轮列表（仍由 {@link com.vagent.chat.RagStreamChatService} 单独拼装）。
 */
@Service
public class ConfigurableQueryRewriteService implements QueryRewriteService {

    private final OrchestrationProperties properties;

    public ConfigurableQueryRewriteService(OrchestrationProperties properties) {
        this.properties = properties;
    }

    @Override
    public RewriteResult rewriteForRetrieval(String currentUserMessage, List<Message> historyPriorToCurrent) {
        String trimmed = currentUserMessage == null ? "" : currentUserMessage.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("currentUserMessage must not be blank");
        }
        if (properties.getRewriteStrategy() == OrchestrationProperties.RewriteStrategy.PASSTHROUGH) {
            return new RewriteResult(trimmed);
        }
        return new RewriteResult(concatUserHistory(trimmed, historyPriorToCurrent));
    }

    private String concatUserHistory(String currentUserMessage, List<Message> historyPriorToCurrent) {
        int maxSeg = Math.max(1, properties.getConcatMaxUserSegments());
        int needPrior = maxSeg - 1;
        List<String> segments = new ArrayList<>();
        if (historyPriorToCurrent != null && needPrior > 0) {
            for (int i = historyPriorToCurrent.size() - 1; i >= 0 && segments.size() < needPrior; i--) {
                Message m = historyPriorToCurrent.get(i);
                if (Message.ROLE_USER.equals(m.getRole())) {
                    String c = m.getContent();
                    if (c != null && !c.isBlank()) {
                        segments.add(c.trim());
                    }
                }
            }
        }
        java.util.Collections.reverse(segments);
        StringBuilder sb = new StringBuilder();
        for (String s : segments) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(s);
        }
        if (!sb.isEmpty()) {
            sb.append('\n');
        }
        sb.append(currentUserMessage);
        return sb.toString();
    }
}
