package com.vagent.kb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 向量检索请求：查询句 + Top-K。
 */
public class RetrieveRequest {

    @NotBlank
    private String query;

    @Min(1)
    @Max(50)
    private int topK = 5;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
