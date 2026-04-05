package com.vagent.chat;

import com.vagent.chat.dto.StreamChatRequest;
import com.vagent.security.VagentUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 会话内 SSE 流式对话（首包事件含 {@code taskId}，供 {@link ChatTaskController} 取消）。
 */
@RestController
public class StreamChatController {

    private final StreamChatService streamChatService;

    public StreamChatController(StreamChatService streamChatService) {
        this.streamChatService = streamChatService;
    }

    @PostMapping(value = "/api/v1/conversations/{conversationId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal VagentUserPrincipal principal,
            @PathVariable String conversationId,
            @Valid @RequestBody StreamChatRequest request) {
        return streamChatService.stream(principal.getUserId(), conversationId, request.getMessage());
    }
}
