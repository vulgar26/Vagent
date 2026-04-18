package com.vagent.conversation;

import com.vagent.conversation.dto.ConversationResponse;
import com.vagent.conversation.dto.CreateConversationRequest;
import com.vagent.chat.LlmStreamTaskRegistry;
import com.vagent.security.VagentUserPrincipal;
import com.vagent.user.UserIdFormats;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话 REST API：需携带有效 JWT（Bearer）。
 * <p>
 * <b>作用：</b> 提供后续流程所需的 {@code conversationId} 来源；创建、列表与删除；消息由级联删除，并尽量取消本会话下进行中的 SSE 任务。
 */
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final LlmStreamTaskRegistry taskRegistry;

    public ConversationController(
            ConversationService conversationService, LlmStreamTaskRegistry taskRegistry) {
        this.conversationService = conversationService;
        this.taskRegistry = taskRegistry;
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

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal VagentUserPrincipal principal,
            @PathVariable String conversationId) {
        String uid = UserIdFormats.canonical(principal.getUserId());
        taskRegistry.cancelAllForConversation(uid, conversationId);
        conversationService.deleteOwned(conversationId, principal.getUserId());
    }
}
