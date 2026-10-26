package com.shaktee.nalok.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Maps domain and request exceptions to stable HTTP problem details. Message
 * strings are user-actionable; body details remain safe because services
 * never embed secrets in errors.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                exception.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    /**
     * A study session pointed at a context target that does not exist: the
     * contract answers "not found" rather than a generic bad request.
     */
    @ExceptionHandler(
            com.shaktee.nalok.knowledge.study.service.ContextTargetNotFoundException.class)
    ProblemDetail contextTargetNotFound(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                exception.getMessage());
        problem.setTitle("Context target not found");
        return problem;
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(org.springframework.http.converter.HttpMessageNotReadableException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Malformed request body");
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                String.format("Parameter '%s' is invalid", exception.getName()));
        problem.setTitle("Invalid request");
        return problem;
    }

    /**
     * Mapped ahead of the generic IllegalStateException handler so a busy
     * guard is reported as retryable/unavailable, not a client conflict.
     */
    @ExceptionHandler(
            com.shaktee.nalok.knowledge.study.service.GuardUnavailableException.class)
    ProblemDetail serviceUnavailable(Exception exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                exception.getMessage());
        problem.setTitle("Temporary unavailability");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail invalidParameters(HandlerMethodValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request parameters or fields are invalid");
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(org.springframework.web.bind.MethodArgumentNotValidException exception) {
        String firstMessage = exception.getBindingResult().getFieldErrors().isEmpty()
                ? "Request body is invalid"
                : exception.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, firstMessage);
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                exception.getMessage());
        problem.setTitle("Operation conflicts with current state");
        return problem;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail optimisticLockConflict(ObjectOptimisticLockingFailureException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The record changed concurrently; reload and try again");
        problem.setTitle("Concurrent update");
        return problem;
    }
}
