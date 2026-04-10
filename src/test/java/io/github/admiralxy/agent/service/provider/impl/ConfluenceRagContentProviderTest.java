package io.github.admiralxy.agent.service.provider.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfluenceRagContentProviderTest {

    private static final String PATH = "/rest/api/content/563256891";
    private static final String QUERY = "expand=body.storage";

    @Test
    void supportsConfluenceOnly() {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider();
        assertTrue(provider.supports(ProviderType.CONFLUENCE));
        assertFalse(provider.supports(ProviderType.TEXT));
    }

    @Test
    void resolveContentReturnsTitleAndStorageValue() throws IOException {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider();
        HttpServer server = createServer(200, """
                {
                  "title": "Стенды",
                  "body": {
                    "storage": {
                      "value": "<p>Контент</p>"
                    }
                  }
                }
                """);
        server.start();

        try {
            StepVerifier.create(provider.resolveContent(buildUrl(server)))
                    .expectNext("Стенды\n\n<p>Контент</p>")
                    .verifyComplete();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveContentThrowsOnNonSuccessfulStatus() throws IOException {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider();
        HttpServer server = createServer(500, "{}");
        server.start();

        try {
            StepVerifier.create(provider.resolveContent(buildUrl(server)))
                    .expectError(IllegalStateException.class)
                    .verify();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveContentThrowsOnInvalidJson() throws IOException {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider();
        HttpServer server = createServer(200, "not-json");
        server.start();

        try {
            StepVerifier.create(provider.resolveContent(buildUrl(server)))
                    .expectError(IllegalStateException.class)
                    .verify();
        } finally {
            server.stop(0);
        }
    }

    private HttpServer createServer(int status, String responseBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(PATH, exchange -> writeResponse(exchange, status, responseBody));
        return server;
    }

    private String buildUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort() + PATH + "?" + QUERY;
    }

    private void writeResponse(HttpExchange exchange, int status, String responseBody) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
