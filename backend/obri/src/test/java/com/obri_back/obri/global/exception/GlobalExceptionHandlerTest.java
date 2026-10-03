package com.obri_back.obri.global.exception;

import com.obri_back.obri.global.common.APIResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDataIntegrityViolationException_returns409() {
        DataIntegrityViolationException e =
                new DataIntegrityViolationException("FK constraint violation");

        ResponseEntity<APIResponse<Void>> response =
                handler.handleDataIntegrityViolationException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getMessage())
                .isEqualTo("연관된 데이터가 있어 처리할 수 없습니다");
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
