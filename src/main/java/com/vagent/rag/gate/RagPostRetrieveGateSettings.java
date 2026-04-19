package com.vagent.rag.gate;

import com.vagent.eval.EvalApiProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 解析 {@link RagPostRetrieveGate} 使用的距离阈值与子串列表：<strong>{@code vagent.rag.gate.*} 优先</strong>，
 * 未配置时回退到 {@link EvalApiProperties} 中已弃用的 {@code vagent.eval.api.*} 同义键，保证旧环境 yml 仍生效。
 */
@Component
public class RagPostRetrieveGateSettings {

    private final RagGateProperties ragGateProperties;
    private final EvalApiProperties evalApiProperties;

    public RagPostRetrieveGateSettings(RagGateProperties ragGateProperties, EvalApiProperties evalApiProperties) {
        this.ragGateProperties = ragGateProperties;
        this.evalApiProperties = evalApiProperties;
    }

    /** 非 {@code null} 时参与距离低置信判定；{@code null} 表示关闭该规则。 */
    public Double lowConfidenceCosineDistanceThreshold() {
        if (ragGateProperties.getLowConfidenceCosineDistanceThreshold() != null) {
            return ragGateProperties.getLowConfidenceCosineDistanceThreshold();
        }
        return evalApiProperties.getLowConfidenceCosineDistanceThreshold();
    }

    public List<String> lowConfidenceQuerySubstrings() {
        List<String> fromGate = ragGateProperties.getLowConfidenceQuerySubstrings();
        if (fromGate != null && !fromGate.isEmpty()) {
            return List.copyOf(fromGate);
        }
        return evalApiProperties.getLowConfidenceQuerySubstrings();
    }
}
