package com.vagent.rag.gate;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 与 {@link RagPostRetrieveGate} 配套的主链路门控配置，前缀 {@code vagent.rag.gate}。
 * <p>
 * 用于 SSE 与 {@code POST /api/v1/eval/chat} 共用阈值，避免误读为「仅评测接口」配置。
 * 若未设置，则回退到 {@link com.vagent.eval.EvalApiProperties} 中已弃用的同义字段（兼容旧 yml）。
 */
@ConfigurationProperties(prefix = "vagent.rag.gate")
public class RagGateProperties {

    /**
     * 余弦距离门控：首条命中距离 <strong>大于</strong>该值视为低置信；{@code null} 表示未在本前缀配置，将读 eval 回退。
     */
    private Double lowConfidenceCosineDistanceThreshold;

    /**
     * 命中非空时：query 包含任一条子串则低置信；默认空列表表示未在本前缀配置子串列表，将读 eval 回退。
     */
    private List<String> lowConfidenceQuerySubstrings = new ArrayList<>();

    public Double getLowConfidenceCosineDistanceThreshold() {
        return lowConfidenceCosineDistanceThreshold;
    }

    public void setLowConfidenceCosineDistanceThreshold(Double lowConfidenceCosineDistanceThreshold) {
        this.lowConfidenceCosineDistanceThreshold = lowConfidenceCosineDistanceThreshold;
    }

    public List<String> getLowConfidenceQuerySubstrings() {
        return lowConfidenceQuerySubstrings;
    }

    public void setLowConfidenceQuerySubstrings(List<String> lowConfidenceQuerySubstrings) {
        this.lowConfidenceQuerySubstrings =
                lowConfidenceQuerySubstrings != null ? lowConfidenceQuerySubstrings : new ArrayList<>();
    }
}
