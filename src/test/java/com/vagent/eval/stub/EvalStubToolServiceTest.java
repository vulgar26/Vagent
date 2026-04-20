package com.vagent.eval.stub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vagent.eval.EvalApiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 轻量单测：不拉起完整 Spring 上下文，仅验证桩工具 JSON→Schema→文案链路。 */
class EvalStubToolServiceTest {

    @Test
    void case001WeatherAnswerMatchesLegacyShape() {
        EvalApiProperties p = new EvalApiProperties();
        EvalStubToolService svc =
                new EvalStubToolService(p, new EvalStubToolPayloadValidator(new ObjectMapper()), new ObjectMapper());
        EvalStubToolService.Result r = svc.runStub("p0_v0_tool_001", "");
        assertTrue(r.succeeded());
        assertEquals(
                "【桩-天气】北京当前天气：晴，气温 18～26℃，北风 2 级（评测桩数据，非实时）。",
                r.answer());
    }

    @Test
    void schemaValidationOffStillSucceeds() {
        EvalApiProperties p = new EvalApiProperties();
        p.setStubToolJsonSchemaValidationEnabled(false);
        EvalStubToolService svc =
                new EvalStubToolService(p, new EvalStubToolPayloadValidator(new ObjectMapper()), new ObjectMapper());
        EvalStubToolService.Result r = svc.runStub("p0_v0_tool_002", "");
        assertTrue(r.succeeded());
        assertEquals("stub_train", r.toolName());
    }
}
