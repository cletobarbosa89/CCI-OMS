package com.cci.oms.infrastructure.filter;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @Test
    void doFilter_noHeaderInRequest_generatesCorrelationIdResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
    }

    @Test
    void doFilter_headerPresentInRequest_propagatesSameId() throws Exception {
        String existingId = "test-correlation-id-123";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(existingId);
    }

    @Test
    void doFilter_mdcIsPopulatedDuringChainExecution() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedId = new AtomicReference<>();
        // Filter is a @FunctionalInterface in Jakarta Servlet 5 (only doFilter is abstract)
        Filter captureFilter = (req, res, chain) -> capturedId.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        MockFilterChain chain = new MockFilterChain(mock(jakarta.servlet.Servlet.class), captureFilter);

        filter.doFilterInternal(request, response, chain);

        assertThat(capturedId.get()).isNotBlank();
        assertThat(capturedId.get()).isEqualTo(response.getHeader(CorrelationIdFilter.HEADER_NAME));
    }

    @Test
    void doFilter_clearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
