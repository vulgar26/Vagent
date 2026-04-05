package com.vagent.chat;

import com.vagent.security.VagentUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 取消进行中的流式生成任务（与 SSE 首包中的 {@code taskId} 对应）。
 */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatTaskController {

    private final StreamChatService streamChatService;

    public ChatTaskController(StreamChatService streamChatService) {
        this.streamChatService = streamChatService;
    }

    @PostMapping("/tasks/{taskId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @AuthenticationPrincipal VagentUserPrincipal principal,
            @PathVariable String taskId) {
        if (!streamChatService.cancel(principal.getUserId(), taskId)) {
            throw new ResponseStatusException(NOT_FOUND, "任务不存在或无权取消");
        }
    }
}
