package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.LimitExceededException;
import io.github.admiralxy.agent.exception.RequestTooLargeException;
import io.github.admiralxy.agent.exception.StoredFileNotFoundException;
import io.github.admiralxy.agent.exception.UploadConflictException;
import io.github.admiralxy.agent.exception.UploadDecryptionException;
import io.github.admiralxy.agent.exception.UploadExpiredException;
import io.github.admiralxy.agent.exception.UploadNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {FileController.class, FileUploadController.class})
public class FileControllerAdvice {

    private static final String NOT_JSON = "Request body is not valid JSON";
    private static final String HEADER_REQUIRED = "Header '%s' is required";
    private static final String PARAMETER_INVALID = "Parameter '%s' has invalid format";

    @ExceptionHandler({StoredFileNotFoundException.class, UploadNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UploadExpiredException.class)
    public ProblemDetail handleUploadExpired(UploadExpiredException e) {
        return problem(HttpStatus.GONE, e.getMessage());
    }

    @ExceptionHandler(UploadConflictException.class)
    public ProblemDetail handleUploadConflict(UploadConflictException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail handleInvalid(InvalidFileException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof RequestTooLargeException tooLarge) {
                return handleRequestTooLarge(tooLarge);
            }
        }
        return problem(HttpStatus.BAD_REQUEST, NOT_JSON);
    }

    @ExceptionHandler(RequestTooLargeException.class)
    public ProblemDetail handleRequestTooLarge(RequestTooLargeException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }

    @ExceptionHandler(UploadDecryptionException.class)
    public ProblemDetail handleDecryption(UploadDecryptionException e) {
        return problem(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException e) {
        return problem(HttpStatus.BAD_REQUEST, HEADER_REQUIRED.formatted(e.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, PARAMETER_INVALID.formatted(e.getName()));
    }

    @ExceptionHandler(LimitExceededException.class)
    public ProblemDetail handleLimitExceeded(LimitExceededException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
