package com.vagent.eval.stub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalStubToolPayloadValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final EvalStubToolPayloadValidator validator = new EvalStubToolPayloadValidator(mapper);

    @Test
    void validWeatherPayloadPasses() {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", "eval_stub_weather");
        n.put("city", "北京");
        n.put("summary", "晴");
        n.put("temp_min_c", 18);
        n.put("temp_max_c", 26);
        n.put("wind_bft", 2);
        assertTrue(validator.validate("stub_weather", n).isEmpty());
    }

    @Test
    void wrongWeatherKindFails() {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", "wrong");
        n.put("city", "北京");
        n.put("summary", "晴");
        n.put("temp_min_c", 18);
        n.put("temp_max_c", 26);
        n.put("wind_bft", 2);
        assertFalse(validator.validate("stub_weather", n).isEmpty());
    }

    @Test
    void validTrainPayloadPasses() {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", "eval_stub_train");
        n.put("from_station", "上海虹桥");
        n.put("to_station", "杭州东");
        n.put("train_no", "G7551");
        n.put("depart_time", "08:12");
        n.put("duration_minutes", 45);
        assertTrue(validator.validate("stub_train", n).isEmpty());
    }

    @Test
    void invalidTrainNoPatternFails() {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", "eval_stub_train");
        n.put("from_station", "上海虹桥");
        n.put("to_station", "杭州东");
        n.put("train_no", "g7551");
        n.put("depart_time", "08:12");
        n.put("duration_minutes", 45);
        assertFalse(validator.validate("stub_train", n).isEmpty());
    }

    @Test
    void validSearchPayloadPasses() {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", "eval_stub_search");
        ArrayNode arr = n.putArray("restaurant_names");
        arr.add("A");
        assertTrue(validator.validate("stub_search", n).isEmpty());
    }

    @Test
    void stubUnknownSkipsValidation() {
        ObjectNode n = mapper.createObjectNode();
        n.put("x", 1);
        assertTrue(validator.validate("stub_unknown", n).isEmpty());
    }
}
