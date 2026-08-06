package org.di.digital.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()
        );
        problem.setTitle("Business Rule Violation");
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, ex.getMessage()
        );
        problem.setTitle("Forbidden");
        return problem;
    }
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage()
        );
        problem.setTitle("Not Found");
        return problem;
    }
    @ExceptionHandler(RestClientResponseException.class)
    public ProblemDetail handleFaceAuthError(RestClientResponseException ex) {
        log.warn("FaceAuth error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());

        String code = null;
        String message = null;
        try {
            Map body = new ObjectMapper()
                    .readValue(ex.getResponseBodyAsString(), Map.class);
            code = (String) body.get("code");
            message = (String) body.get("message");
            if (message == null) message = (String) body.get("detail");
        } catch (Exception ignore) { }

        String userMessage = mapFaceCode(code, message);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, userMessage);
        problem.setTitle("Face ID Error");
        if (code != null) problem.setProperty("code", code);
        return problem;
    }
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
        problem.setTitle("Internal Server Error");
        return problem;
    }

    private String mapFaceCode(String code, String fallback) {
        if (code == null) return fallback != null ? fallback : "Ошибка проверки лица";
        return switch (code) {
            case "IU011"              -> "Требуется эталонное фото лица. Пройдите регистрацию Face ID заново.";
            case "LIVENESS_FAILED"    -> "Обнаружено фото или экран. Используйте живое лицо.";
            case "MATCH_FAILED"       -> "Лицо не совпадает с сохранённым эталоном.";
            case "POSE_NOT_CONFIRMED" -> "Поза не распознана. Следуйте инструкциям на экране.";
            case "NO_FACE"            -> "Лицо не обнаружено. Убедитесь, что лицо в кадре.";
            case "INCONSISTENT_FACES" -> "На кадрах разные лица. Повторите попытку.";
            case "INTERNAL"           -> "Внутренняя ошибка обработки лица. Повторите попытку.";
            default                   -> fallback != null ? fallback : "Ошибка проверки лица";
        };
    }
}