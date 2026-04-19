package com.vagent.guardrails;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 门控与一次性反思（评测接口 P0）。
 *
 * <p>P0 仅规则判定，无 LLM 反思输出；若后续接入 LLM reflection，解析失败应按 vagent-upgrade 降级为
 * {@code deny|clarify} 且 {@code error_code=PARSE_ERROR}，并遵守单次修复上限，禁止循环重试。</p>
 */
@ConfigurationProperties(prefix = "vagent.guardrails")
public class GuardrailsProperties {

    private Reflection reflection = new Reflection();
    private QuoteOnly quoteOnly = new QuoteOnly();

    public Reflection getReflection() {
        return reflection;
    }

    public void setReflection(Reflection reflection) {
        this.reflection = reflection != null ? reflection : new Reflection();
    }

    public QuoteOnly getQuoteOnly() {
        return quoteOnly;
    }

    public void setQuoteOnly(QuoteOnly quoteOnly) {
        this.quoteOnly = quoteOnly != null ? quoteOnly : new QuoteOnly();
    }

    /**
     * Quote-only：与题集 {@code quote_only=true} 配合；服务端总开关关闭时请求字段无效。
     * 判定规则见 {@code plans/quote-only-guardrails.md}。
     */
    public static class QuoteOnly {

        /** 默认关闭，避免改变既有 eval 基线。 */
        private boolean enabled = false;

        /**
         * {@code relaxed|moderate|strict}，不区分大小写；无法解析时回退 {@code moderate}。
         */
        private String strictness = "moderate";

        /**
         * 为 true 时：主对话 SSE（{@link com.vagent.chat.RagStreamChatService}）在 RAG 有命中且走 LLM 时，
         * 先缓冲全文再发 {@code meta+chunk}，以便与 eval 同源执行 quote-only（默认 false，避免改变既有流式体验）。
         */
        private boolean applyToSseStream = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStrictness() {
            return strictness;
        }

        public void setStrictness(String strictness) {
            this.strictness = strictness != null ? strictness : "moderate";
        }

        public boolean isApplyToSseStream() {
            return applyToSseStream;
        }

        public void setApplyToSseStream(boolean applyToSseStream) {
            this.applyToSseStream = applyToSseStream;
        }
    }

    public static class Reflection {

        /**
         * 是否启用评测接口内一次性门控（引用闭环、低置信长度等）；默认关闭以免改变既有 eval 基线。
         */
        private boolean enabled = false;

        /**
         * 当 {@code meta.low_confidence=true} 时，若 {@code answer} 超过该长度则拒答并 {@code GUARDRAIL_TRIGGERED}。
         */
        private int maxAnswerCharsWhenLowConfidence = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAnswerCharsWhenLowConfidence() {
            return maxAnswerCharsWhenLowConfidence;
        }

        public void setMaxAnswerCharsWhenLowConfidence(int maxAnswerCharsWhenLowConfidence) {
            this.maxAnswerCharsWhenLowConfidence = maxAnswerCharsWhenLowConfidence;
        }
    }
}
