package com.wangnu.allargando.global.config;

import com.wangnu.allargando.global.ratelimit.RateLimitInterceptor;
import com.wangnu.allargando.global.ratelimit.RateLimiter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * MVC 설정 — API 요청에 호출 빈도 제한 인터셉터를 건다.
 * 인터셉터를 빈으로 등록하지 않고 여기서 만드는 이유: @WebMvcTest는 WebMvcConfigurer만 불러오고
 * RateLimiter 같은 일반 빈은 불러오지 않는데, ObjectProvider로 받으면 빈이 없어도 설정이 뜬다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<RateLimiter> rateLimiter;

    public WebMvcConfig(ObjectProvider<RateLimiter> rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter)).addPathPatterns("/api/**");
    }
}
