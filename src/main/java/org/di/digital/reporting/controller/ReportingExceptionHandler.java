package org.di.digital.reporting.controller;

import lombok.extern.slf4j.Slf4j;
import org.di.digital.reporting.validation.ReportingValidationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Reporting-only handlers, checked before GlobalExceptionHandler. Without them request and conflict
 * errors fall into its catch-all Exception handler and become 500, and validation errors lose their
 * per-cell details. Everything else (404, 403, ...) still goes to GlobalExceptionHandler.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "org.di.digital.reporting")
public class ReportingExceptionHandler {

    @ExceptionHandler(ReportingValidationException.class)
    public ProblemDetail handleValidation(ReportingValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Report Validation Failed");
        problem.setProperty("errors", ex.getErrors());
        if (!ex.getCellErrors().isEmpty()) {
            problem.setProperty("cellErrors", ex.getCellErrors());
        }
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidRequest(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, String.join("; ", errors));
        problem.setTitle("Invalid Request");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Missing or malformed JSON body, e.g. cells sent as something other than a JSON object. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request body is missing or is not valid JSON of the expected shape");
        problem.setTitle("Invalid Request");
        return problem;
    }

    /** Path or query parameter of the wrong type, e.g. a date not in yyyy-MM-dd or a non-numeric version. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'");
        problem.setTitle("Invalid Request");
        return problem;
    }

    // DataIntegrityViolationException: a unique constraint lost a race (e.g. two first saves of the same report)
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        log.warn("Reporting conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The data was changed by another request. Reload and try again.");
        problem.setTitle("Conflict");
        return problem;
    }
}
