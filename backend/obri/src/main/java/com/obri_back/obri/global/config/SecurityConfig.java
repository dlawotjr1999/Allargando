package com.obri_back.obri.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obri_back.obri.global.common.APIResponse;
import com.obri_back.obri.global.security.FirebaseAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/*
 * Spring Security 설정 클래스
 * 세션 없는(STATELESS) 토큰 기반 인증 구성 — FirebaseAuthFilter를 인증 필터로 등록하고
 * 인증 예외 경로(회원가입·닉네임 체크·Swagger)를 화이트리스트로 개방
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final FirebaseAuthFilter firebaseAuthFilter;

    // 네이티브 앱 런타임은 CORS 대상이 아님 — Expo web(브라우저)으로 붙는 로컬 개발 편의용(BACKLOG.md #36).
    // 운영 웹 도메인이 아직 없어 기본값은 빈 문자열(= 전 오리진 차단), 로컬은 application-local.properties에서 오버라이드
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    /*
     * SecurityFilterChain 빈 등록
     * CSRF 비활성화·세션 STATELESS 설정 후 경로별 인가 규칙을 지정하고
     * FirebaseAuthFilter를 UsernamePasswordAuthenticationFilter 앞에 삽입
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // 인증 불필요
                .requestMatchers("/api/auth/register").permitAll()
                .requestMatchers("/api/users/check/**").permitAll()
                // Swagger 관련 경로 추가
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                // 헬스체크 — PaaS/로드밸런서/오케스트레이터의 기동 확인용(BACKLOG.md #28)
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            .addFilterBefore(firebaseAuthFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /*
     * CORS 설정 — 네이티브 앱은 대상이 아니므로 app.cors.allowed-origins가 비어있으면 전 오리진 차단(안전 기본값)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if (!allowedOrigins.isBlank()) {
            configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
            configuration.setAllowedHeaders(List.of("*"));
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /*
     * 미인증 요청 처리
     * 기본 Http403ForbiddenEntryPoint(403 + 빈 바디) 대신 명세의 401 + APIResponse 형식으로 응답
     * 단, 토큰은 유효한데 가입된 유저가 없는 경우(필터가 표식을 남김)는 404 — 클라이언트가 "토큰 만료"와
     * "가입 미완료"를 구분해 가입 화면으로 보낼 수 있게 한다(D18)
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            if (request.getAttribute(FirebaseAuthFilter.UNREGISTERED_USER_ATTRIBUTE) != null) {
                writeError(response, HttpServletResponse.SC_NOT_FOUND, "가입되지 않은 사용자입니다");
                return;
            }
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "인증이 필요합니다");
        };
    }

    /*
     * 권한 없는 요청 처리
     * 기본 AccessDeniedHandler(403 + 빈 바디) 대신 APIResponse 형식으로 응답
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN, "접근 권한이 없습니다");
    }

    // 필터 단계의 인증·인가 실패는 컨트롤러 밖이라 GlobalExceptionHandler가 못 받으므로 같은 응답 형식으로 직접 쓴다
    private void writeError(HttpServletResponse response, int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(APIResponse.error(status, message)));
    }
}