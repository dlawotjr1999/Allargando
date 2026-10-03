package com.obri_back.obri.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/*
 * Firebase 초기화 및 빈 등록 설정 클래스
 * 서버 시작 시 Firebase Admin SDK를 초기화하고
 * FirebaseAuth 빈을 Spring Context에 등록
 */
@Configuration
public class FirebaseConfig {

    // 서비스 계정 키 위치 — 로컬·CI는 classpath(resources), 운영은 file:로 컨테이너 밖에서 마운트한 경로.
    // classpath로 고정하면 키가 Docker 이미지 안에 구워지므로 위치를 주입받는다
    @Value("${firebase.credentials.location}")
    private Resource credentialsLocation;

    /*
     * Firebase 앱 초기화
     * firebase.credentials.location의 서비스 계정 키를 읽어 인증 정보를 설정하고
     * FirebaseApp 인스턴스를 생성 후 빈으로 등록
     * 이미 초기화된 경우 기존 인스턴스를 반환 (중복 초기화 방지)

     * return : 초기화된 FirebaseApp 인스턴스
     */
    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        // 서비스 계정 키 파일로 인증 정보 생성
        GoogleCredentials credentials;
        try (InputStream keyStream = credentialsLocation.getInputStream()) {
            credentials = GoogleCredentials.fromStream(keyStream);
        }

        // Firebase 옵션 설정
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        return FirebaseApp.initializeApp(options);
    }

    /*
     * FirebaseAuth 빈 등록
     * 토큰 검증, 유저 조회 등 Firebase 인증 관련 작업에 사용
     * FirebaseAuthFilter에서 주입받아 토큰 검증에 활용
     *
     * param : firebaseApp 초기화된 FirebaseApp 인스턴스
     * return : FirebaseAuth 인스턴스
    */
    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    /*
     * FirebaseMessaging 빈 등록
     * FCM 푸시(신규 구인글 broadcast, 지원 결과 단건 push 등)에 사용
     */
    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}