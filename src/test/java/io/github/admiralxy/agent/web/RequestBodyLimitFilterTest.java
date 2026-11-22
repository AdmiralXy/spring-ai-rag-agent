package io.github.admiralxy.agent.web;

import io.github.admiralxy.agent.exception.RequestTooLargeException;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestBodyLimitFilterTest {

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(10);

    @Test
    void bodyWithinLimitIsReadable() throws Exception {
        ServletRequest wrapped = filtered(new byte[10], true);

        assertArrayEquals(new byte[10], wrapped.getInputStream().readAllBytes());
    }

    @Test
    void bodyOverLimitFailsWhileReading() throws Exception {
        ServletRequest wrapped = filtered(new byte[11], false);

        assertThrows(RequestTooLargeException.class, () -> wrapped.getInputStream().readAllBytes());
    }

    @Test
    void declaredContentLengthOverLimitFailsOnFirstRead() throws Exception {
        ServletRequest wrapped = filtered(new byte[11], true);

        assertThrows(RequestTooLargeException.class, () -> wrapped.getInputStream().read());
    }

    private ServletRequest filtered(byte[] body, boolean declareLength) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/files/uploads/x");
        request.setContent(body);
        if (!declareLength) {
            request = new MockHttpServletRequest("PUT", "/files/uploads/x") {
                @Override
                public long getContentLengthLong() {
                    return -1;
                }
            };
            request.setContent(body);
        }
        AtomicReference<ServletRequest> captured = new AtomicReference<>();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> captured.set(req));
        return captured.get();
    }
}
