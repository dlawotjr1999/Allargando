package com.wangnu.allargando.global.config;

import com.wangnu.allargando.global.ratelimit.RateLimitInterceptor;
import com.wangnu.allargando.global.ratelimit.RateLimiter;
import com.wangnu.allargando.global.version.AppVersionInterceptor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * MVC 설정 — API 요청에 강제 업데이트 검사와 호출 빈도 제한 인터셉터를 건다(이 순서로 실행된다).
 * 인터셉터를 빈으로 등록하지 않고 여기서 만드는 이유: @WebMvcTest는 WebMvcConfigurer만 불러오고
 * RateLimiter 같은 일반 빈은 불러오지 않는데, ObjectProvider로 받으면 빈이 없어도 설정이 뜬다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<RateLimiter> rateLimiter;
    private final String minAppVersion;

    public WebMvcConfig(ObjectProvider<RateLimiter> rateLimiter,
                        @Value("${app.min-version:}") String minAppVersion) {
        this.rateLimiter = rateLimiter;
        this.minAppVersion = minAppVersion;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AppVersionInterceptor(minAppVersion)).addPathPatterns("/api/**");
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter)).addPathPatterns("/api/**");
    }
}
