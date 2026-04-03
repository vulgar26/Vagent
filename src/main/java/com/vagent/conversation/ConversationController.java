package com.vagent.conversation;

import com.vagent.conversation.dto.ConversationResponse;
import com.vagent.conversation.dto.CreateConversationRequest;
import com.vagent.security.VagentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话 REST API：需携带有效 JWT（Bearer）。
 * <p>
 * <b>作用：</b> 提供后续流程所需的 {@code conversationId} 来源；当前仅创建与列表，消息与记忆在后续里程碑追加。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public List<ConversationResponse> list(@AuthenticationPrincipal VagentUserPrincipal principal) {
        return conversationService.listForUser(principal.getUserId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(
            @AuthenticationPrincipal VagentUserPrincipal principal,
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        if (request == null) {
            request = new CreateConversationRequest();
        }
        return conversationService.create(principal.getUserId(), request);
    }
}
