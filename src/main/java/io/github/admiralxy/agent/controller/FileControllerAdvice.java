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

/**
 * Error mapping for file endpoints. Errors are returned as RFC 9457 problem details.
 */
@RestControllerAdvice(assignableTypes = {FileController.class, FileUploadController.class})
public class FileControllerAdvice {

    @ExceptionHandler(StoredFileNotFoundException.class)
    public ProblemDetail handleNotFound(StoredFileNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UploadNotFoundException.class)
    public ProblemDetail handleUploadNotFound(UploadNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UploadExpiredException.class)
    public ProblemDetail handleUploadExpired(UploadExpiredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
    }

    @ExceptionHandler(UploadConflictException.class)
    public ProblemDetail handleUploadConflict(UploadConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ProblemDetail handleInvalid(InvalidFileException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof RequestTooLargeException tooLarge) {
                return handleRequestTooLarge(tooLarge);
            }
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is not valid JSON");
    }

    @ExceptionHandler(RequestTooLargeException.class)
    public ProblemDetail handleRequestTooLarge(RequestTooLargeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }

    @ExceptionHandler(UploadDecryptionException.class)
    public ProblemDetail handleDecryption(UploadDecryptionException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Header '" + e.getHeaderName() + "' is required");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parameter '" + e.getName() + "' has invalid format");
    }

    @ExceptionHandler(LimitExceededException.class)
    public ProblemDetail handleLimitExceeded(LimitExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }
}
