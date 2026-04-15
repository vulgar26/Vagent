package com.vagent.kb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalPatternBuilderTest {

    @Test
    void escapesPercentAndUnderscoreForLike() {
        String p = LexicalPatternBuilder.buildContainsPattern("100%_done", 100);
        assertThat(p).isEqualTo("%100\\%\\_done%");
    }
}
