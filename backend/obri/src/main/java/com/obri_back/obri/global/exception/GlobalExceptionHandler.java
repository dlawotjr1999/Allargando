package com.obri_back.obri.global.exception;

import com.obri_back.obri.global.common.APIResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
     * 첫 번째 에러 필드와 메시지를 반환
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<Void>> handleValidationException(
            MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        String message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.error(400, message));
    }

    /*
     * 409 Conflict
     * DB 제약(FK·UNIQUE) 위반 시 — 예: 다른 리소스가 참조 중인 유저 삭제, 중복 전화번호 저장
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse<Void>> handleDataIntegrityViolationException(
            DataIntegrityViolationException e) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(APIResponse.error(409, "연관된 데이터가 있어 처리할 수 없습니다"));
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