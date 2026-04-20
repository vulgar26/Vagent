package com.vagent.eval.stub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vagent.eval.EvalApiProperties;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * vagent-eval {@code tool_policy=stub} 时的进程内桩工具：满足
 * {@code tool.required && tool.used && tool.succeeded} 判定，不依赖外部 MCP。
 *
 * <p>成功路径下先构造结构化 JSON payload，再按 classpath 中的 JSON Schema（Draft 2020-12）校验，
 * 最后由 payload 渲染对评测可见的中文 {@link Result#answer()}。</p>
 */
@Service
public final class EvalStubToolService {

    private final EvalApiProperties evalApiProperties;
    private final EvalStubToolPayloadValidator payloadValidator;
    private final ObjectMapper objectMapper;
    private final EvalStubToolCircuitBreaker circuitBreaker;
    private final ExecutorService executor =
            Executors.newCachedThreadPool(
                    r -> {
                        Thread t = new Thread(r, "eval-stub-tool");
                        t.setDaemon(true);
                        return t;
                    });

    public EvalStubToolService(
            EvalApiProperties evalApiProperties,
            EvalStubToolPayloadValidator payloadValidator,
            ObjectMapper objectMapper) {
        this.evalApiProperties = evalApiProperties;
        this.payloadValidator = payloadValidator;
        this.objectMapper = objectMapper;
        this.circuitBreaker =
                new EvalStubToolCircuitBreaker(
                        evalApiProperties.getStubToolCircuitFailureThreshold(),
                        evalApiProperties.getStubToolCircuitOpenSeconds());
    }

    /**
     * @param caseId 可选 {@code X-Eval-Case-Id}，用于映射 p0_v0_tool_* 题
     */
    public Result runStub(String caseId, String query) {
        String key = resolveToolKey(caseId, query);
        if (!circuitBreaker.allow(key)) {
            return new Result(key, "工具暂时不可用（熔断中），请稍后重试。", "error", false, 0L);
        }
        long t0 = System.nanoTime();
        Future<Result> f =
                executor.submit(
                        () -> {
                            try {
                                return doInvoke(key);
                            } catch (Exception e) {
                                return new Result(key, "桩工具执行失败。", "error", false, elapsedMs(t0));
                            }
                        });
        try {
            Result r = f.get(evalApiProperties.getStubToolTimeoutMs(), TimeUnit.MILLISECONDS);
            long ms = elapsedMs(t0);
            Result timed = new Result(r.toolName(), r.answer(), r.outcome(), r.succeeded(), ms);
            if (timed.succeeded()) {
                circuitBreaker.recordSuccess(key);
            } else {
                circuitBreaker.recordFailure(key);
            }
            return timed;
        } catch (TimeoutException e) {
            f.cancel(true);
            circuitBreaker.recordFailure(key);
            return new Result(key, "工具调用超时。", "timeout", false, evalApiProperties.getStubToolTimeoutMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            circuitBreaker.recordFailure(key);
            return new Result(key, "工具调用被中断。", "error", false, elapsedMs(t0));
        } catch (ExecutionException e) {
            circuitBreaker.recordFailure(key);
            return new Result(key, "工具调用失败。", "error", false, elapsedMs(t0));
        }
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private Result doInvoke(String toolKey) {
        return switch (toolKey) {
            case "stub_weather" -> finalizePayload(toolKey, buildWeatherPayload());
            case "stub_train" -> finalizePayload(toolKey, buildTrainPayload());
            case "stub_search" -> finalizePayload(toolKey, buildSearchPayload());
            default ->
                    new Result(
                            toolKey,
                            "未识别的桩工具场景。", "error", false, 0L);
        };
    }

    private Result finalizePayload(String toolKey, ObjectNode payload) {
        if (evalApiProperties.isStubToolJsonSchemaValidationEnabled()) {
            Optional<String> err = payloadValidator.validate(toolKey, payload);
            if (err.isPresent()) {
                return new Result(toolKey, "桩工具输出校验失败。", "error", false, 0L);
            }
        }
        return new Result(toolKey, formatAnswer(toolKey, payload), "success", true, 0L);
    }

    private ObjectNode buildWeatherPayload() {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("kind", "eval_stub_weather");
        n.put("city", "北京");
        n.put("summary", "晴");
        n.put("temp_min_c", 18);
        n.put("temp_max_c", 26);
        n.put("wind_bft", 2);
        return n;
    }

    private ObjectNode buildTrainPayload() {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("kind", "eval_stub_train");
        n.put("from_station", "上海虹桥");
        n.put("to_station", "杭州东");
        n.put("train_no", "G7551");
        n.put("depart_time", "08:12");
        n.put("duration_minutes", 45);
        return n;
    }

    private ObjectNode buildSearchPayload() {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("kind", "eval_stub_search");
        ArrayNode arr = n.putArray("restaurant_names");
        arr.add("评测桩餐厅 A");
        arr.add("评测桩餐厅 B");
        arr.add("评测桩餐厅 C");
        return n;
    }

    private static String formatAnswer(String toolKey, ObjectNode payload) {
        return switch (toolKey) {
            case "stub_weather" ->
                    "【桩-天气】"
                            + payload.get("city").asText()
                            + "当前天气："
                            + payload.get("summary").asText()
                            + "，气温 "
                            + payload.get("temp_min_c").asInt()
                            + "～"
                            + payload.get("temp_max_c").asInt()
                            + "℃，北风 "
                            + payload.get("wind_bft").asInt()
                            + " 级（评测桩数据，非实时）。";
            case "stub_train" ->
                    "【桩-车次】"
                            + payload.get("from_station").asText()
                            + "→"
                            + payload.get("to_station").asText()
                            + "示例："
                            + payload.get("train_no").asText()
                            + " "
                            + payload.get("depart_time").asText()
                            + " 开，约 "
                            + payload.get("duration_minutes").asInt()
                            + " 分钟到（评测桩数据）。";
            case "stub_search" -> {
                StringBuilder sb = new StringBuilder("【桩-餐厅】示例高分餐厅：");
                ArrayNode names = (ArrayNode) payload.get("restaurant_names");
                for (int i = 0; i < names.size(); i++) {
                    if (i > 0) {
                        sb.append(" / ");
                    }
                    sb.append(names.get(i).asText());
                }
                sb.append("（评测桩数据，非实时榜单）。");
                yield sb.toString();
            }
            default -> "";
        };
    }

    static String resolveToolKey(String caseId, String query) {
        String cid = caseId == null ? "" : caseId.trim();
        if ("p0_v0_tool_001".equals(cid)) {
            return "stub_weather";
        }
        if ("p0_v0_tool_002".equals(cid)) {
            return "stub_train";
        }
        if ("p0_v0_tool_003".equals(cid)) {
            return "stub_search";
        }
        String norm = qnorm(query);
        if (norm.contains("天气")) {
            return "stub_weather";
        }
        if (norm.contains("高铁") || norm.contains("车次") || norm.contains("时刻表")) {
            return "stub_train";
        }
        if (norm.contains("餐厅") || norm.contains("评分")) {
            return "stub_search";
        }
        return "stub_unknown";
    }

    private static String qnorm(String query) {
        if (query == null) {
            return "";
        }
        return query.toLowerCase(Locale.ROOT);
    }

    public record Result(String toolName, String answer, String outcome, boolean succeeded, long latencyMs) {
        public Result {
            toolName = Objects.toString(toolName, "");
            answer = Objects.toString(answer, "");
            outcome = Objects.toString(outcome, "error");
        }
    }
}
