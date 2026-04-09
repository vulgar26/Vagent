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
     * P0 归因码（附录 D 枚举）。成功时可为 null。
     */
    private String errorCode;

    public EvalChatResponse() {}

    public EvalChatResponse(
            String answer,
            String behavior,
            long latencyMs,
            Capabilities capabilities,
            Map<String, Object> meta,
            List<Source> sources,
            String errorCode) {
        this.answer = answer;
        this.behavior = behavior;
        this.latencyMs = latencyMs;
        this.capabilities = capabilities;
        this.meta = meta;
        this.sources = sources;
        this.errorCode = errorCode;
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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
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

