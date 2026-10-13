package com.wangnu.allargando.global.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.wangnu.allargando.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/*
 * Firebase ID Token 검증 필터
 * 모든 요청에서 Authorization 헤더의 Firebase ID Token을 검증하고
 * 유효한 경우 SecurityContext에 인증 정보를 저장
 * OncePerRequestFilter를 상속해 요청당 한 번만 실행됨을 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseAuthFilter extends OncePerRequestFilter {

    // 토큰은 유효한데 DB에 유저가 없을 때(Firebase 계정만 있고 가입 미완료) 요청에 남기는 표식(D18).
    // 필터는 예외를 던지지 않으므로, 인증 실패 처리기(SecurityConfig)가 이 값으로 401과 404를 가른다
    public static final String UNREGISTERED_USER_ATTRIBUTE = "allargando.unregisteredUser";

    // 토큰이 틀린 것이 아니라 Firebase나 DB에 닿지 못해 인증을 판정하지 못했을 때 남기는 표식.
    // 인증 실패 처리기가 이 값이면 401 대신 503으로 응답한다 — 앱이 401을 받고 로그아웃시키지 않게 한다
    public static final String AUTH_UNAVAILABLE_ATTRIBUTE = "allargando.authUnavailable";

    private final FirebaseAuth firebaseAuth;
    private final UserRepository userRepository;

    /**
     * 요청마다 실행되는 필터 메서드
     * Authorization 헤더에서 Bearer 토큰을 추출해 Firebase로 검증하고
     * 검증 성공 시 firebase_uid로 DB 유저를 조회해 SecurityContext에 저장
     * 검증 실패 또는 토큰 없는 경우 SecurityContext를 비운 채 다음 필터로 진행
     * Firebase·DB 장애로 판정하지 못한 경우에는 로그를 남기고 표식(AUTH_UNAVAILABLE_ATTRIBUTE)을 남긴다
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // 토큰 없으면 다음 필터로
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = header.substring(7);

        // "Bearer " 뒤가 비어있으면 검증을 시도하지 않는다 — verifyIdToken은 빈 토큰에
        // IllegalArgumentException을 던지는데, 필터에서 던진 예외는 DispatcherServlet 이전이라
        // GlobalExceptionHandler가 잡지 못하고 컨테이너 기본 에러(비 JSON 500)로 나간다
        if (idToken.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Firebase 토큰 검증
            FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
            String firebaseUid = decodedToken.getUid();

            // firebase_uid로 DB 유저 조회
            userRepository.findByFirebaseUid(firebaseUid).ifPresentOrElse(user -> {
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        user, null, Collections.emptyList()
                    );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }, () -> request.setAttribute(UNREGISTERED_USER_ATTRIBUTE, true));

        } catch (FirebaseAuthException e) {
            // 토큰 검증 실패 → SecurityContext 비운 채로 다음 필터로
            // (인증이 필요한 경로면 AuthenticationEntryPoint가 401 + APIResponse 형식으로 응답)
            SecurityContextHolder.clearContext();
            // 만료·위조 같은 토큰 문제는 흔하므로 로그를 남기지 않고, Firebase 장애만 남긴다
            if (FirebaseAuthFailures.isServiceFailure(e)) {
                log.warn("Firebase 토큰 검증 불가 — Firebase 장애로 보임 ({})", FirebaseAuthFailures.describe(e));
                request.setAttribute(AUTH_UNAVAILABLE_ATTRIBUTE, true);
            }
        } catch (IllegalArgumentException e) {
            // 형식이 깨진 토큰 → 토큰 문제
            SecurityContextHolder.clearContext();
        } catch (DataAccessException e) {
            // 유저 조회 실패(DB 장애) — 필터에서 던지면 전역 핸들러가 못 받아 비 JSON 오류가 나간다.
            // 예외 메시지에는 SQL과 값이 들어갈 수 있어 종류만 남긴다
            SecurityContextHolder.clearContext();
            log.error("인증 필터의 사용자 조회 실패 — DB 장애로 보임 ({})", e.getClass().getSimpleName());
            request.setAttribute(AUTH_UNAVAILABLE_ATTRIBUTE, true);
        }

        filterChain.doFilter(request, response);
    }
}