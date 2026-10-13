package com.wangnu.allargando.global.ratelimit;

import com.wangnu.allargando.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 인터셉터가 한도를 넘으면 컨트롤러에 닿기 전에 429 + Retry-After로 응답하는지 확인한다
class RateLimitInterceptorTest {

    @RestController
    static class ProbeController {
        @GetMapping("/api/probe/limited")
        @RateLimit(limit = 2, windowSeconds = 60, by = RateLimit.By.IP)
        public String limited() {
            return "ok";
        }

        @GetMapping("/api/probe/free")
        public String free() {
            return "ok";
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<RateLimiter> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(new RateLimiter());
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addInterceptors(new RateLimitInterceptor(provider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void annotatedEndpoint_returns429WithRetryAfterWhenLimitExceeded() throws Exception {
        mockMvc.perform(get("/api/probe/limited")).andExpect(status().isOk());
        mockMvc.perform(get("/api/probe/limited")).andExpect(status().isOk());

        mockMvc.perform(get("/api/probe/limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void baseline_blocksEveryEndpointAfter120CallsPerMinute() throws Exception {
        for (int i = 0; i < RateLimitInterceptor.BASELINE_LIMIT; i++) {
            mockMvc.perform(get("/api/probe/free")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/probe/free")).andExpect(status().isTooManyRequests());
    }

    @Test
    void withoutRateLimiterBean_everythingPasses() throws Exception {
        @SuppressWarnings("unchecked")
        ObjectProvider<RateLimiter> empty = mock(ObjectProvider.class);
        given(empty.getIfAvailable()).willReturn(null);
        MockMvc open = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addInterceptors(new RateLimitInterceptor(empty))
                .build();

        for (int i = 0; i < 200; i++) {
            open.perform(get("/api/probe/limited")).andExpect(status().isOk());
        }
    }
}
