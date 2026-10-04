package com.wangnu.allargando.concert.kopis;

/*
 * KOPIS 오픈API 호출 실패 시 발생 (네트워크 오류, 응답 형식 이상 등)
 * 호출부(KopisSyncService)가 페이지 단위로 잡아 로그만 남기고 해당 회차 동기화를 중단한다
 */
public class KopisSyncException extends RuntimeException {
    public KopisSyncException(String message) {
        super(message);
    }

    public KopisSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
