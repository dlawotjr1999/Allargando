package com.obri_back.obri.global.exception;

import com.obri_back.obri.global.common.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.JDBCException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;
import java.util.Map;

/*
 * 전역 예외 처리 핸들러
 * 모든 예외를 ApiResponse 형식으로 변환해 반환
 * @RestControllerAdvice로 모든 컨트롤러에 적용
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /*
     * 404 Not Found
     * 존재하지 않는 리소스 요청 시
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<APIResponse<Void>> handleNotFoundException(NotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(APIResponse.error(404, e.getMessage()));
    }

    /*
     * 403 Forbidden
     * 권한 없는 요청 시
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<APIResponse<Void>> handleForbiddenException(ForbiddenException e) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(APIResponse.error(403, e.getMessage()));
    }

    /*
     * 409 Conflict
     * 중복 데이터 요청 시
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<APIResponse<Void>> handleConflictException(ConflictException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(APIResponse.error(409, e.getMessage()));
    }

    /*
     * 400 Bad Request
     * 잘못된 요청 시
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<APIResponse<Void>> handleBadRequestException(BadRequestException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.error(400, e.getMessage()));
    }

    /*
     * 400 Bad Request - @Valid 검증 실패 시
     * 요청 바디의 필드 유효성 검사 실패
     * 첫 번째 에러 필드와 메시지를 반환(클래스 수준 검증처럼 필드 에러가 없으면 일반 문구)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.error(400, message));
    }

    // PostgreSQL SQLSTATE — Spring·Hibernate가 DataIntegrityViolationException으로 감싼 원인을 구분하는 기준
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String NOT_NULL_VIOLATION = "23502";
    private static final String CHECK_VIOLATION = "23514";

    // 동시 요청이 사전 체크를 둘 다 통과해 UNIQUE에 걸렸을 때 서비스의 사전 체크 메시지와 같은 문구를 돌려주기 위한 매핑.
    // 키는 제약명(소문자). application의 이름은 V1 baseline(수정 금지)에서 Hibernate가 생성한 값이라 고정이다
    // — 스키마가 바뀌어 어긋나면 GlobalExceptionHandlerPostgresTest가 잡는다
    private static final Map<String, String> UNIQUE_MESSAGES = Map.of(
            "uk1m278y6v8jh3b4i9aeetf3dc2", "이미 지원한 모집글입니다",
            "uk_report_reporter_target", "이미 신고한 대상입니다",
            "uk_user_block_pair", "이미 차단한 사용자입니다");

    /*
     * DB 제약 위반 — SQLSTATE로 원인을 구분해 응답하고 원인 로그를 남긴다
     * UNIQUE → 409(알려진 제약은 구체적 메시지), FK → 409(다른 데이터가 참조 중),
     * NOT NULL·길이 초과(22xxx)·CHECK → 400(입력 문제), 분류할 수 없으면 기존 계약대로 409
     * 로그에는 SQLSTATE·제약명만 남긴다 — PostgreSQL 예외 메시지에는 전화번호 같은 실제 값이 들어 있어
     * 메시지·스택트레이스는 남기지 않는다
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException e) {
        JDBCException jdbc = findJdbcException(e);
        String sqlState = jdbc == null ? null : jdbc.getSQLState();
        String constraint = jdbc instanceof ConstraintViolationException cve ? cve.getConstraintName() : null;
        log.warn("[DB 제약 위반] sqlState={}, constraint={}, type={}", sqlState, constraint,
                jdbc == null ? e.getClass().getSimpleName() : jdbc.getClass().getSimpleName());

        if (UNIQUE_VIOLATION.equals(sqlState)) {
            String message = constraint == null ? null : UNIQUE_MESSAGES.get(constraint.toLowerCase(Locale.ROOT));
            return conflict(message != null ? message : "이미 존재하는 데이터입니다");
        }
        if (NOT_NULL_VIOLATION.equals(sqlState) || CHECK_VIOLATION.equals(sqlState)
                || (sqlState != null && sqlState.startsWith("22"))) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(APIResponse.error(400, "입력값이 올바르지 않습니다"));
        }
        // FK 위반(23503)과 분류할 수 없는 경우
        return conflict("연관된 데이터가 있어 처리할 수 없습니다");
    }

    private ResponseEntity<APIResponse<Void>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(APIResponse.error(409, message));
    }

    // 원인 체인에서 Hibernate의 JDBCException(SQLSTATE·제약명 보유)을 찾는다. 없으면 null
    private JDBCException findJdbcException(Throwable e) {
        for (int depth = 0; e != null && depth < 10; depth++, e = e.getCause()) {
            if (e instanceof JDBCException jdbc) {
                return jdbc;
            }
        }
        return null;
    }

    /*
     * 405 Method Not Allowed
     * 지원하지 않는 HTTP 메서드 — 스캐너·오호출이 500 + ERROR 스택트레이스가 되지 않게 한다
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<APIResponse<Void>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (e.getSupportedHttpMethods() != null && !e.getSupportedHttpMethods().isEmpty()) {
            response.allow(e.getSupportedHttpMethods().toArray(new HttpMethod[0]));
        }
        return response.body(APIResponse.error(405, "지원하지 않는 요청 방식입니다"));
    }

    /*
     * 415 Unsupported Media Type
     * 지원하지 않는 Content-Type (예: JSON 엔드포인트에 text/plain)
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<APIResponse<Void>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException e) {
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(APIResponse.error(415, "지원하지 않는 Content-Type입니다"));
    }

    /*
     * 404 Not Found
     * 매핑되지 않은 경로(예: 인증된 사용자가 없는 URL을 호출) — 리소스 없음(NotFoundException)과 별개로 처리
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(APIResponse.error(404, "요청한 경로를 찾을 수 없습니다"));
    }

    /*
     * 400 Bad Request
     * 쿼리 파라미터·경로 변수 타입 불일치 시 (예: ?status=FOO)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<APIResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.error(400, e.getName() + " 파라미터 형식이 올바르지 않습니다"));
    }

    /*
     * 400 Bad Request
     * 필수 요청 헤더 누락 시 (예: Authorization)
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<APIResponse<Void>> handleMissingRequestHeaderException(
            MissingRequestHeaderException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.error(400, e.getHeaderName() + " 헤더가 필요합니다"));
    }

    /*
     * 400 Bad Request
     * 요청 바디를 읽을 수 없을 때 (예: 깨진 JSON)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<APIResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.error(400, "요청 본문을 읽을 수 없습니다"));
    }

    /*
     * 409 Conflict
     * 낙관적 락(@Version) 충돌 시 — 같은 리소스를 동시에 수정한 경우
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<APIResponse<Void>> handleObjectOptimisticLockingFailureException(
            ObjectOptimisticLockingFailureException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(APIResponse.error(409, "다른 요청으로 인해 처리할 수 없습니다. 다시 시도해주세요"));
    }

    /*
     * 401 Unauthorized
     * 인증 실패 시 (예: Firebase ID Token 검증 실패)
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<APIResponse<Void>> handleUnauthorizedException(UnauthorizedException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(APIResponse.error(401, e.getMessage()));
    }

    /*
     * 500 Internal Server Error
     * 예상치 못한 서버 에러
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handleException(Exception e) {
        // BACKLOG.md #23: 운영에서 500이 나면 원인을 추적할 수 있도록 스택트레이스를 남김
        log.error("예상치 못한 서버 오류", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(APIResponse.error(500, "서버 오류가 발생했습니다"));
    }
}