package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.exception.InvalidFileException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionAdvice {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handle(ApiException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail handleInvalid(InvalidFileException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
