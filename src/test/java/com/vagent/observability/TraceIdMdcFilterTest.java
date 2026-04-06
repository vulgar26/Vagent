package com.vagent.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdMdcFilterTest {

    @Test
    void generatesTraceIdAndClearsMdc() throws Exception {
        TraceIdMdcFilter filter = new TraceIdMdcFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(
                req,
                res,
                (request, response) -> {
                    assertThat(MDC.get(TraceIdMdcFilter.MDC_TRACE_ID)).isNotNull();
                });
        assertThat(res.getHeader(TraceIdMdcFilter.RESPONSE_TRACE_HEADER)).isNotNull();
        assertThat(MDC.get(TraceIdMdcFilter.MDC_TRACE_ID)).isNull();
    }

    @Test
    void respectsRequestIdHeader() throws Exception {
        TraceIdMdcFilter filter = new TraceIdMdcFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TraceIdMdcFilter.REQUEST_TRACE_HEADER, "req-abc");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(
                req,
                res,
                (request, response) -> {
                    assertThat(MDC.get(TraceIdMdcFilter.MDC_TRACE_ID)).isEqualTo("req-abc");
                });
        assertThat(res.getHeader(TraceIdMdcFilter.RESPONSE_TRACE_HEADER)).isEqualTo("req-abc");
    }
}
