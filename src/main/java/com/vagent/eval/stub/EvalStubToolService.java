package com.vagent.eval.stub;

import com.vagent.eval.EvalApiProperties;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * vagent-eval {@code tool_policy=stub} 时的进程内桩工具：满足
 * {@code tool.required && tool.used && tool.succeeded} 判定，不依赖外部 MCP。
 */
@Service
public final class EvalStubToolService {

    private final EvalApiProperties evalApiProperties;
    private final EvalStubToolCircuitBreaker circuitBreaker;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "eval-stub-tool");
        t.setDaemon(true);
        return t;
    });

    public EvalStubToolService(EvalApiProperties evalApiProperties) {
        this.evalApiProperties = evalApiProperties;
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

    private static Result doInvoke(String toolKey) {
        return switch (toolKey) {
            case "stub_weather" ->
                    new Result(
                            toolKey,
                            "【桩-天气】北京当前天气：晴，气温 18～26℃，北风 2 级（评测桩数据，非实时）。",
                            "success",
                            true,
                            0L);
            case "stub_train" ->
                    new Result(
                            toolKey,
                            "【桩-车次】上海虹桥→杭州东示例：G7551 08:12 开，约 45 分钟到（评测桩数据）。",
                            "success",
                            true,
                            0L);
            case "stub_search" ->
                    new Result(
                            toolKey,
                            "【桩-餐厅】示例高分餐厅：评测桩餐厅 A / B / C（评测桩数据，非实时榜单）。",
                            "success",
                            true,
                            0L);
            default ->
                    new Result(
                            toolKey,
                            "未识别的桩工具场景。", "error", false, 0L);
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
