package com.vagent.kb.dto;

import java.util.List;

public class RetrieveResponse {

    private List<RetrieveHit> hits;

    public RetrieveResponse() {
    }

    public RetrieveResponse(List<RetrieveHit> hits) {
        this.hits = hits;
    }

    public List<RetrieveHit> getHits() {
        return hits;
    }

    public void setHits(List<RetrieveHit> hits) {
        this.hits = hits;
    }
}
