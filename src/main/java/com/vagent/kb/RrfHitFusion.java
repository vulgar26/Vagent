package com.vagent.kb;

import com.vagent.kb.dto.RetrieveHit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion（RRF）：融合两路有序候选，避免单一路分数尺度不可比的问题。
 * <p>见 {@code plans/vagent-upgrade.md} P1-0b。</p>
 */
final class RrfHitFusion {

    private RrfHitFusion() {}

    static List<RetrieveHit> fuse(List<RetrieveHit> primaryRanked, List<RetrieveHit> secondaryRanked, int topK, int rrfK) {
        if (topK <= 0) {
            return List.of();
        }
        if (secondaryRanked == null || secondaryRanked.isEmpty()) {
            return takeHead(primaryRanked, topK);
        }
        if (primaryRanked == null || primaryRanked.isEmpty()) {
            return takeHead(secondaryRanked, topK);
        }
        int k = Math.max(1, rrfK);
        Map<String, Double> score = new HashMap<>();
        Map<String, RetrieveHit> bestHit = new HashMap<>();

        // 先向量、后关键词：同 id 时保留向量侧的 RetrieveHit（含 distance 等）
        contribute(primaryRanked, k, score, bestHit);
        contribute(secondaryRanked, k, score, bestHit);

        List<String> ids = new ArrayList<>(score.keySet());
        ids.sort(Comparator.comparingDouble((String id) -> score.getOrDefault(id, 0.0)).reversed());

        List<RetrieveHit> out = new ArrayList<>(Math.min(topK, ids.size()));
        for (String id : ids) {
            if (out.size() >= topK) {
                break;
            }
            RetrieveHit h = bestHit.get(id);
            if (h != null) {
                out.add(h);
            }
        }
        return out;
    }

    private static void contribute(List<RetrieveHit> ranked, int rrfK, Map<String, Double> score, Map<String, RetrieveHit> bestHit) {
        int rank = 1;
        for (RetrieveHit h : ranked) {
            if (h == null || h.getChunkId() == null || h.getChunkId().isBlank()) {
                rank++;
                continue;
            }
            String id = h.getChunkId();
            score.merge(id, 1.0 / (rrfK + rank), Double::sum);
            bestHit.putIfAbsent(id, h);
            rank++;
        }
    }

    private static List<RetrieveHit> takeHead(List<RetrieveHit> in, int topK) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        if (in.size() <= topK) {
            return List.copyOf(in);
        }
        return List.copyOf(in.subList(0, topK));
    }
}
