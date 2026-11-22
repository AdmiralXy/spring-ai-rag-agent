package io.github.admiralxy.agent.service.model;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import io.github.admiralxy.agent.exception.ApiException;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Turns failures of model providers into short explanations a user can act on.
 */
public final class ModelErrors {

    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR = 500;
    private static final int MAX_DETAIL = 300;
    private static final String UNKNOWN_HOST = "the host cannot be resolved, check the base URL";
    private static final String CONNECT = "the server refuses connections, check the base URL";
    private static final String TIMEOUT = "the server did not answer in time";
    private static final String IO = "the server cannot be reached (%s)";
    private static final String AUTH = "the API key was rejected (%d)";
    private static final String MISSING = "the model or the URL was not found (404), check the model name and the base URL";
    private static final String RATE_LIMIT = "rate limit or quota exceeded (429)";
    private static final String PROVIDER = "the provider failed (%d), try again later";
    private static final String STATUS = "the request was rejected (%d): %s";

    private ModelErrors() {
    }

    /**
     * A message for the user; {@link ApiException}s are already readable and are returned as they are.
     */
    public static String describe(Throwable error) {
        for (Throwable e = error; e != null; e = e.getCause()) {
            if (e instanceof ApiException) {
                return e.getMessage();
            }
            if (e instanceof OpenAIServiceException service) {
                return describeStatus(service.statusCode(), service.getMessage());
            }
            if (e instanceof UnknownHostException) {
                return UNKNOWN_HOST;
            }
            if (e instanceof ConnectException) {
                return CONNECT;
            }
            if (e instanceof SocketTimeoutException || e instanceof HttpTimeoutException
                    || e instanceof TimeoutException) {
                return TIMEOUT;
            }
        }
        for (Throwable e = error; e != null; e = e.getCause()) {
            if (e instanceof OpenAIIoException || e instanceof IOException) {
                return IO.formatted(detail(e));
            }
        }
        String message = detail(error);
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return TIMEOUT;
        }
        return message;
    }

    static String describeStatus(int status, String message) {
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            return AUTH.formatted(status);
        }
        if (status == NOT_FOUND) {
            return MISSING;
        }
        if (status == TOO_MANY_REQUESTS) {
            return RATE_LIMIT;
        }
        if (status >= SERVER_ERROR) {
            return PROVIDER.formatted(status);
        }
        return STATUS.formatted(status, StringUtils.abbreviate(StringUtils.defaultString(message), MAX_DETAIL));
    }

    private static String detail(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = StringUtils.defaultIfBlank(root.getMessage(), e.getMessage());
        return StringUtils.abbreviate(StringUtils.defaultIfBlank(message, root.getClass().getSimpleName()), MAX_DETAIL);
    }
}
