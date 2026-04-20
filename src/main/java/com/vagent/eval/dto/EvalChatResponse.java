package com.vagent.eval.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EvalChatResponse {

    private String answer;
    private String behavior;
    private long latencyMs;
    private Capabilities capabilities;
    private Map<String, Object> meta;
    private List<Source> sources;
    /**
     * P1-S1：回答→证据映射（规则提取器生成，禁止自由文本漂移）。
     */
    private List<EvidenceMapItem> evidenceMap;

    /**
     * 评测 citation membership（vagent-eval Day6+）用的根级命中列表；与 {@link #sources} 同源候选口径，元素至少含 {@code id}。
     * S1-D3 起由 {@code EvalChatController} 按 top_n 与检索结果填充；此前可为空数组。
     */
    private List<RetrievalHit> retrievalHits;

    /**
     * P0 归因码（附录 D 枚举）。成功时可为 null。
     */
    private String errorCode;

    /**
     * 与 vagent-eval 契约对齐：{@code expected_behavior=tool} 时须给出 {@code required/used/succeeded} 等字段。
     */
    private Tool tool;

    public EvalChatResponse() {}

    /** 兼容旧调用方：无 {@code tool} 字段时传 {@code null}。 */
    public EvalChatResponse(
            String answer,
            String behavior,
            long latencyMs,
            Capabilities capabilities,
            Map<String, Object> meta,
            List<Source> sources,
            List<RetrievalHit> retrievalHits,
            String errorCode) {
        this(answer, behavior, latencyMs, capabilities, meta, sources, retrievalHits, errorCode, null, null);
    }

    public EvalChatResponse(
            String answer,
            String behavior,
            long latencyMs,
            Capabilities capabilities,
            Map<String, Object> meta,
            List<Source> sources,
            List<RetrievalHit> retrievalHits,
            String errorCode,
            Tool tool,
            List<EvidenceMapItem> evidenceMap) {
        this.answer = answer;
        this.behavior = behavior;
        this.latencyMs = latencyMs;
        this.capabilities = capabilities;
        this.meta = meta;
        this.sources = sources;
        this.retrievalHits = retrievalHits;
        this.errorCode = errorCode;
        this.tool = tool;
        this.evidenceMap = evidenceMap;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public Capabilities getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Capabilities capabilities) {
        this.capabilities = capabilities;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources;
    }

    public List<EvidenceMapItem> getEvidenceMap() {
        return evidenceMap;
    }

    public void setEvidenceMap(List<EvidenceMapItem> evidenceMap) {
        this.evidenceMap = evidenceMap;
    }

    public List<RetrievalHit> getRetrievalHits() {
        return retrievalHits;
    }

    public void setRetrievalHits(List<RetrievalHit> retrievalHits) {
        this.retrievalHits = retrievalHits;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        this.tool = tool;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Tool {
        private boolean required;
        private boolean used;
        private boolean succeeded;
        private String name;
        private String outcome;
        private Long latencyMs;

        public Tool() {}

        public Tool(boolean required, boolean used, boolean succeeded, String name, String outcome, Long latencyMs) {
            this.required = required;
            this.used = used;
            this.succeeded = succeeded;
            this.name = name;
            this.outcome = outcome;
            this.latencyMs = latencyMs;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public boolean isUsed() {
            return used;
        }

        public void setUsed(boolean used) {
            this.used = used;
        }

        public boolean isSucceeded() {
            return succeeded;
        }

        public void setSucceeded(boolean succeeded) {
            this.succeeded = succeeded;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public Long getLatencyMs() {
            return latencyMs;
        }

        public void setLatencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Source {
        private String id;
        private String title;
        private String snippet;

        public Source() {}

        public Source(String id, String title, String snippet) {
            this.id = id;
            this.title = title;
            this.snippet = snippet;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class EvidenceMapItem {
        private String claimType;
        private String claimValue;
        private String claimPath;
        private List<String> sourceIds;
        private Double confidence;

        public EvidenceMapItem() {}

        public EvidenceMapItem(
                String claimType,
                String claimValue,
                String claimPath,
                List<String> sourceIds,
                Double confidence) {
            this.claimType = claimType;
            this.claimValue = claimValue;
            this.claimPath = claimPath;
            this.sourceIds = sourceIds;
            this.confidence = confidence;
        }

        public String getClaimType() {
            return claimType;
        }

        public void setClaimType(String claimType) {
            this.claimType = claimType;
        }

        public String getClaimValue() {
            return claimValue;
        }

        public void setClaimValue(String claimValue) {
            this.claimValue = claimValue;
        }

        public String getClaimPath() {
            return claimPath;
        }

        public void setClaimPath(String claimPath) {
            this.claimPath = claimPath;
        }

        public List<String> getSourceIds() {
            return sourceIds;
        }

        public void setSourceIds(List<String> sourceIds) {
            this.sourceIds = sourceIds;
        }

        public Double getConfidence() {
            return confidence;
        }

        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
    }

    /**
     * 根级 {@code retrieval_hits[]} 元素；与 kb {@code RetrieveHit} 区分命名，避免混用。
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class RetrievalHit {
        private String id;
        /** 可选：相似度/距离导出分数字段，与 eval SSOT 对齐时填入。 */
        private Double score;

        public RetrievalHit() {}

        public RetrievalHit(String id) {
            this.id = id;
        }

        public RetrievalHit(String id, Double score) {
            this.id = id;
            this.score = score;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Capabilities {
        private CapabilityFlag retrieval;
        private CapabilityFlag tools;
        private StreamingFlag streaming;
        private GuardrailsFlag guardrails;

        public Capabilities() {}

        public Capabilities(CapabilityFlag retrieval, CapabilityFlag tools, StreamingFlag streaming, GuardrailsFlag guardrails) {
            this.retrieval = retrieval;
            this.tools = tools;
            this.streaming = streaming;
            this.guardrails = guardrails;
        }

        public CapabilityFlag getRetrieval() {
            return retrieval;
        }

        public void setRetrieval(CapabilityFlag retrieval) {
            this.retrieval = retrieval;
        }

        public CapabilityFlag getTools() {
            return tools;
        }

        public void setTools(CapabilityFlag tools) {
            this.tools = tools;
        }

        public StreamingFlag getStreaming() {
            return streaming;
        }

        public void setStreaming(StreamingFlag streaming) {
            this.streaming = streaming;
        }

        public GuardrailsFlag getGuardrails() {
            return guardrails;
        }

        public void setGuardrails(GuardrailsFlag guardrails) {
            this.guardrails = guardrails;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class CapabilityFlag {
        private boolean supported;
        private Boolean score;
        private Boolean outcome;

        public CapabilityFlag() {}

        public CapabilityFlag(boolean supported) {
            this.supported = supported;
        }

        public CapabilityFlag(boolean supported, Boolean score, Boolean outcome) {
            this.supported = supported;
            this.score = score;
            this.outcome = outcome;
        }

        public boolean isSupported() {
            return supported;
        }

        public void setSupported(boolean supported) {
            this.supported = supported;
        }

        public Boolean getScore() {
            return score;
        }

        public void setScore(Boolean score) {
            this.score = score;
        }

        public Boolean getOutcome() {
            return outcome;
        }

        public void setOutcome(Boolean outcome) {
            this.outcome = outcome;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class StreamingFlag {
        private boolean ttft;

        public StreamingFlag() {}

        public StreamingFlag(boolean ttft) {
            this.ttft = ttft;
        }

        public boolean isTtft() {
            return ttft;
        }

        public void setTtft(boolean ttft) {
            this.ttft = ttft;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class GuardrailsFlag {
        private boolean quoteOnly;
        private boolean evidenceMap;
        private boolean reflection;

        public GuardrailsFlag() {}

        public GuardrailsFlag(boolean quoteOnly, boolean evidenceMap, boolean reflection) {
            this.quoteOnly = quoteOnly;
            this.evidenceMap = evidenceMap;
            this.reflection = reflection;
        }

        public boolean isQuoteOnly() {
            return quoteOnly;
        }

        public void setQuoteOnly(boolean quoteOnly) {
            this.quoteOnly = quoteOnly;
        }

        public boolean isEvidenceMap() {
            return evidenceMap;
        }

        public void setEvidenceMap(boolean evidenceMap) {
            this.evidenceMap = evidenceMap;
        }

        public boolean isReflection() {
            return reflection;
        }

        public void setReflection(boolean reflection) {
            this.reflection = reflection;
        }
    }
}

