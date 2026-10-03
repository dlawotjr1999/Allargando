package com.obri_back.obri.global.exception;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.obri_back.obri.global.common.APIResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // 원인 정보가 없는(SQLSTATE를 알 수 없는) 제약 위반은 기존 계약대로 409 + 일반 문구
    @Test
    void handleDataIntegrityViolationException_returns409WhenUnclassified() {
        DataIntegrityViolationException e =
                new DataIntegrityViolationException("FK constraint violation");

        ResponseEntity<APIResponse<Void>> response =
                handler.handleDataIntegrityViolationException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getMessage())
                .isEqualTo("연관된 데이터가 있어 처리할 수 없습니다");
    }

    private DataIntegrityViolationException violation(String sqlState, String constraint, String message) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException(message, new SQLException(message, sqlState), constraint));
    }

    // 알려진 UNIQUE 제약은 서비스의 사전 체크와 같은 문구로 409
    @Test
    void handleDataIntegrityViolationException_usesSpecificMessageForKnownUniqueConstraint() {
        assertThat(handler.handleDataIntegrityViolationException(
                violation("23505", "uk1m278y6v8jh3b4i9aeetf3dc2", "dup")).getBody().getMessage())
                .isEqualTo("이미 지원한 모집글입니다");
        assertThat(handler.handleDataIntegrityViolationException(
                violation("23505", "UK_USER_BLOCK_PAIR", "dup")).getBody().getMessage())
                .isEqualTo("이미 차단한 사용자입니다");
        assertThat(handler.handleDataIntegrityViolationException(
                violation("23505", "uk_report_reporter_target", "dup")).getBody().getMessage())
                .isEqualTo("이미 신고한 대상입니다");
    }

    @Test
    void handleDataIntegrityViolationException_returns409WithGenericDuplicateMessageForUnknownUnique() {
        ResponseEntity<APIResponse<Void>> response = handler.handleDataIntegrityViolationException(
                violation("23505", "uk_something_new", "dup"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("이미 존재하는 데이터입니다");
    }

    @Test
    void handleDataIntegrityViolationException_returns409ForForeignKeyViolation() {
        ResponseEntity<APIResponse<Void>> response = handler.handleDataIntegrityViolationException(
                violation("23503", "fk_report_reporter", "fk"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("연관된 데이터가 있어 처리할 수 없습니다");
    }

    // NOT NULL·CHECK·길이 초과 같은 입력 문제는 409가 아니라 400
    @Test
    void handleDataIntegrityViolationException_returns400ForNotNullAndCheckViolation() {
        for (String sqlState : List.of("23502", "23514")) {
            ResponseEntity<APIResponse<Void>> response = handler.handleDataIntegrityViolationException(
                    violation(sqlState, null, "bad"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().getMessage()).isEqualTo("입력값이 올바르지 않습니다");
        }
    }

    @Test
    void handleDataIntegrityViolationException_returns400ForValueTooLong() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("could not execute statement",
                new DataException("value too long", new SQLException("value too long", "22001")));

        ResponseEntity<APIResponse<Void>> response = handler.handleDataIntegrityViolationException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
    }

    // 로그에는 SQLSTATE·제약명만 남기고, 실제 값(전화번호 등)이 든 예외 메시지·스택트레이스는 남기지 않는다
    @Test
    void handleDataIntegrityViolationException_logsCauseWithoutPersonalData() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            handler.handleDataIntegrityViolationException(violation("23505", "uk_user_block_pair",
                    "ERROR: duplicate key value violates unique constraint Key (phone_number)=(010-1234-5678) already exists"));

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).contains("23505").contains("uk_user_block_pair")
                        .doesNotContain("010-1234-5678");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void handleHttpRequestMethodNotSupportedException_returns405WithAllowHeader() {
        ResponseEntity<APIResponse<Void>> response = handler.handleHttpRequestMethodNotSupportedException(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().getStatus()).isEqualTo(405);
        assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
    }

    @Test
    void handleHttpMediaTypeNotSupportedException_returns415() {
        ResponseEntity<APIResponse<Void>> response = handler.handleHttpMediaTypeNotSupportedException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().getStatus()).isEqualTo(415);
    }

    @Test
    void handleNoResourceFoundException_returns404() {
        ResponseEntity<APIResponse<Void>> response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "/api/nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }

    // 클래스 수준 검증처럼 필드 에러가 하나도 없어도 IndexOutOfBounds(500) 없이 400
    @Test
    void handleValidationException_returns400WhenNoFieldErrors() {
        MethodArgumentNotValidException e = new MethodArgumentNotValidException(
                mock(MethodParameter.class), new BeanPropertyBindingResult(new Object(), "request"));

        ResponseEntity<APIResponse<Void>> response = handler.handleValidationException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("요청 값이 올바르지 않습니다");
    }

    @Test
    void handleMethodArgumentTypeMismatchException_returns400() {
        MethodArgumentTypeMismatchException e = mock(MethodArgumentTypeMismatchException.class);
        given(e.getName()).willReturn("status");

        ResponseEntity<APIResponse<Void>> response =
                handler.handleMethodArgumentTypeMismatchException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("status 파라미터 형식이 올바르지 않습니다");
    }

    @Test
    void handleMissingRequestHeaderException_returns400() {
        MissingRequestHeaderException e = mock(MissingRequestHeaderException.class);
        given(e.getHeaderName()).willReturn("Authorization");

        ResponseEntity<APIResponse<Void>> response =
                handler.handleMissingRequestHeaderException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("Authorization 헤더가 필요합니다");
    }

    @Test
    void handleHttpMessageNotReadableException_returns400() {
        HttpMessageNotReadableException e = mock(HttpMessageNotReadableException.class);

        ResponseEntity<APIResponse<Void>> response =
                handler.handleHttpMessageNotReadableException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isEqualTo("요청 본문을 읽을 수 없습니다");
    }

    @Test
    void handleObjectOptimisticLockingFailureException_returns409() {
        ObjectOptimisticLockingFailureException e =
                new ObjectOptimisticLockingFailureException(Object.class, 1L);

        ResponseEntity<APIResponse<Void>> response =
                handler.handleObjectOptimisticLockingFailureException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getMessage())
                .isEqualTo("다른 요청으로 인해 처리할 수 없습니다. 다시 시도해주세요");
    }

    @Test
    void handleUnauthorizedException_returns401() {
        UnauthorizedException e = new UnauthorizedException("유효하지 않은 Firebase 토큰입니다");

        ResponseEntity<APIResponse<Void>> response = handler.handleUnauthorizedException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getMessage()).isEqualTo("유효하지 않은 Firebase 토큰입니다");
    }
}
