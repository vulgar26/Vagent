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

    public Reflection getReflection() {
        return reflection;
    }

    public void setReflection(Reflection reflection) {
        this.reflection = reflection != null ? reflection : new Reflection();
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
