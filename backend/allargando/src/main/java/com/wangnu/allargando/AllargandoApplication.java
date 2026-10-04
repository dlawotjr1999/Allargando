package com.wangnu.allargando;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/*
 * Allargando 백엔드 애플리케이션 진입점 (Spring Boot 부트스트랩)
 * KOPIS 연주회 동기화(KopisSyncScheduler)의 @Scheduled 실행을 위해 EnableScheduling 활성화
 */
@SpringBootApplication
@EnableScheduling
public class AllargandoApplication {

	// 애플리케이션 실행
	public static void main(String[] args) {
		SpringApplication.run(AllargandoApplication.class, args);
	}

}
