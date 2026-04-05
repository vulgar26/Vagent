package com.vagent.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 入库一篇文档（标题 + 正文，服务端分块与嵌入）。
 */
public class IngestDocumentRequest {

    @NotBlank
    @Size(max = 512)
    private String title;

    @NotBlank
    private String content;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
