package com.vagent.kb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryTextLimiterTest {

    @Test
    void truncatesLongInput() {
        String s = "a".repeat(20);
        assertThat(QueryTextLimiter.trimAndLimit(s, 10)).hasSize(10);
    }
}
