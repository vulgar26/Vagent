package com.vagent.orchestration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 改写策略、意图规则阈值与澄清文案，前缀 {@code vagent.orchestration}。
 */
@ConfigurationProperties(prefix = "vagent.orchestration")
public class OrchestrationProperties {

    /**
     * 是否启用意图分支；为 false 时行为与纯 M4 一致（仅改写仍可按策略作用于检索 query）。
     */
    private boolean intentEnabled = true;

    /**
     * 检索 query 的构造方式：透传本轮用户句，或将最近若干轮 USER 拼接后再 embed。
     */
    private RewriteStrategy rewriteStrategy = RewriteStrategy.PASSTHROUGH;

    /**
     * {@link RewriteStrategy#CONCAT_USER_HISTORY} 时：参与拼接的「用户句」最大条数（含本轮），≥1。
     * 例如为 3 时：最多取历史中 2 条 USER + 本轮，共 3 段用换行拼接。
     */
    private int concatMaxUserSegments = 3;

    /**
     * 用户输入 trim 后长度 **小于** 该值且未命中寒暄规则时，走 {@link com.vagent.orchestration.model.ChatBranch#CLARIFICATION}。
     */
    private int clarificationMinChars = 3;

    /**
     * 逗号分隔的前缀列表（忽略大小写）：匹配则视为寒暄，走 SYSTEM_DIALOG 而不检索。
     */
    private String systemDialogPrefixes = "你好,嗨,hi,hello,谢谢,再见,在吗,早上好,晚安";

    /**
     * 澄清分支时推给用户的固定引导句（不经 LLM）。
     */
    private String clarificationTemplate =
            "您的描述较简短，请补充具体场景或关键词（例如涉及哪条流程、哪个系统），方便我准确回答。";

    /**
     * U7：是否启用“工具意图”（显式触发 MCP 工具调用）。默认关闭，避免 prompt 注入或误触发。
     */
    private boolean toolIntentEnabled = false;

    /**
     * U7：逗号分隔关键词（忽略大小写）；命中则视为“工具意图”，允许在 RAG 分支中调用 MCP 工具。
     * <p>
     * 建议使用偏“明确指令”的关键词，避免误触发，例如：mcp,tool,工具,调用工具
     */
    private String toolIntentKeywords = "mcp,tool,工具,调用工具";

    /**
     * U7：工具意图命中时默认调用的 tool name（需同时在 MCP 侧存在，并通过白名单校验）。
     * 例如 Python demo 可用 echo/ping。
     */
    private String toolIntentDefaultToolName = "echo";

    public boolean isIntentEnabled() {
        return intentEnabled;
    }

    public void setIntentEnabled(boolean intentEnabled) {
        this.intentEnabled = intentEnabled;
    }

    public RewriteStrategy getRewriteStrategy() {
        return rewriteStrategy;
    }

    public void setRewriteStrategy(RewriteStrategy rewriteStrategy) {
        this.rewriteStrategy = rewriteStrategy;
    }

    public int getConcatMaxUserSegments() {
        return concatMaxUserSegments;
    }

    public void setConcatMaxUserSegments(int concatMaxUserSegments) {
        this.concatMaxUserSegments = concatMaxUserSegments;
    }

    public int getClarificationMinChars() {
        return clarificationMinChars;
    }

    public void setClarificationMinChars(int clarificationMinChars) {
        this.clarificationMinChars = clarificationMinChars;
    }

    public String getSystemDialogPrefixes() {
        return systemDialogPrefixes;
    }

    public void setSystemDialogPrefixes(String systemDialogPrefixes) {
        this.systemDialogPrefixes = systemDialogPrefixes;
    }

    public String getClarificationTemplate() {
        return clarificationTemplate;
    }

    public void setClarificationTemplate(String clarificationTemplate) {
        this.clarificationTemplate = clarificationTemplate;
    }

    public boolean isToolIntentEnabled() {
        return toolIntentEnabled;
    }

    public void setToolIntentEnabled(boolean toolIntentEnabled) {
        this.toolIntentEnabled = toolIntentEnabled;
    }

    public String getToolIntentKeywords() {
        return toolIntentKeywords;
    }

    public void setToolIntentKeywords(String toolIntentKeywords) {
        this.toolIntentKeywords = toolIntentKeywords;
    }

    public String getToolIntentDefaultToolName() {
        return toolIntentDefaultToolName;
    }

    public void setToolIntentDefaultToolName(String toolIntentDefaultToolName) {
        this.toolIntentDefaultToolName = toolIntentDefaultToolName;
    }

    public enum RewriteStrategy {
        PASSTHROUGH,
        CONCAT_USER_HISTORY
    }
}
