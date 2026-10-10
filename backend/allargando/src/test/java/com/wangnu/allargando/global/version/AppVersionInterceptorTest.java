package com.wangnu.allargando.global.version;

import com.wangnu.allargando.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 강제 업데이트: 최소 버전 미만만 426, 헤더가 없거나 읽을 수 없거나 최소 버전이 비어 있으면 통과
class AppVersionInterceptorTest {

    @RestController
    static class Probe {
        @GetMapping("/api/probe")
        public String probe() {
            return "ok";
        }
    }

    private MockMvc mvc(String minimum) {
        return MockMvcBuilders.standaloneSetup(new Probe())
                .addInterceptors(new AppVersionInterceptor(minimum))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void belowMinimum_returns426() throws Exception {
        mvc("1.2.0").perform(get("/api/probe").header("X-App-Version", "1.1.9"))
                .andExpect(status().isUpgradeRequired())
                .andExpect(jsonPath("$.status").value(426));
    }

    @Test
    void atOrAboveMinimum_passes() throws Exception {
        mvc("1.2.0").perform(get("/api/probe").header("X-App-Version", "1.2.0")).andExpect(status().isOk());
        mvc("1.2.0").perform(get("/api/probe").header("X-App-Version", "1.10.0")).andExpect(status().isOk());
    }

    // 버전을 보내기 전의 빌드와 앱 밖 호출(Swagger, curl)은 막지 않는다
    @Test
    void missingHeader_passes() throws Exception {
        mvc("9.9.9").perform(get("/api/probe")).andExpect(status().isOk());
    }

    @Test
    void unreadableHeader_passes() throws Exception {
        mvc("9.9.9").perform(get("/api/probe").header("X-App-Version", "dev-build"))
                .andExpect(status().isOk());
    }

    @Test
    void emptyMinimum_disablesTheCheck() throws Exception {
        mvc("").perform(get("/api/probe").header("X-App-Version", "0.0.1")).andExpect(status().isOk());
    }
}
