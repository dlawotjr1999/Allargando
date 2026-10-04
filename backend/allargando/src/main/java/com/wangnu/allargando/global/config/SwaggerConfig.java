package com.wangnu.allargando.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * Swagger(OpenAPI) 문서 설정 클래스
 * Bearer(Firebase ID Token) 인증 스킴을 전역 등록해 Swagger UI의 Authorize로 토큰을 넣을 수 있게 함
 */
@Configuration
public class SwaggerConfig {

    /*
     * OpenAPI 빈 등록
     * Bearer Auth 보안 스킴(JWT 형식)을 정의하고 전역 SecurityRequirement로 적용
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .addSecurityItem(new SecurityRequirement().addList("Bearer Auth"))
            .components(new Components()
                .addSecuritySchemes("Bearer Auth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Firebase ID Token을 입력해주세요")
                ));
    }
}