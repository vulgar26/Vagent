package com.vagent.kb;

import com.vagent.kb.dto.RetrieveHit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * U5：主路 + 第二路命中按 {@link RetrieveHit#getChunkId()} 去重（同 id 保留距离更小者），再按距离升序截断。
 */
public final class RetrieveHitMerge {

    private RetrieveHitMerge() {}

    public static List<RetrieveHit> mergeAndTakeTop(List<RetrieveHit> primary, List<RetrieveHit> second, int limit) {
        Map<String, RetrieveHit> byChunkId = new LinkedHashMap<>();
        for (RetrieveHit h : primary) {
            putBetter(byChunkId, h);
        }
        for (RetrieveHit h : second) {
            putBetter(byChunkId, h);
        }
        List<RetrieveHit> merged = new ArrayList<>(byChunkId.values());
        merged.sort(Comparator.comparingDouble(RetrieveHit::getDistance));
        if (merged.size() > limit) {
            return new ArrayList<>(merged.subList(0, limit));
        }
        return merged;
    }

    private static void putBetter(Map<String, RetrieveHit> map, RetrieveHit h) {
        if (h == null || h.getChunkId() == null) {
            return;
        }
        map.merge(
                h.getChunkId(),
                h,
                (a, b) -> Double.compare(a.getDistance(), b.getDistance()) <= 0 ? a : b);
    }
}
