package io.github.admiralxy.agent.service.provider.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.config.properties.ConfluenceProperties;
import io.github.admiralxy.agent.config.properties.RagProperties;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfluenceRagContentProviderTest {

    private static final String PATH = "/rest/api/content/563256891";
    private static final String QUERY = "expand=body.storage";
    private static final String USERNAME = "user";
    private static final String PASSWORD = "pass";

    @Test
    void supportsConfluenceOnly() {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(withCredentials(USERNAME, PASSWORD));
        assertTrue(provider.supports(ProviderType.CONFLUENCE));
        assertFalse(provider.supports(ProviderType.TEXT));
    }

    @Test
    void resolveContentReturnsTitleAndStorageValue() throws IOException {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(withCredentials(USERNAME, PASSWORD));
        AtomicReference<String> authorizationRef = new AtomicReference<>();
        HttpServer server = createServer(200, """
                {
                  "title": "Стенды",
                  "body": {
                    "storage": {
                      "value": "<p>Контент</p>"
                    }
                  }
                }
                """, authorizationRef);
        server.start();

        try {
            StepVerifier.create(provider.resolveContent(buildUrl(server)))
                    .expectNext("Стенды\n\n<p>Контент</p>")
                    .verifyComplete();
            assertEquals(expectedBasicAuth(USERNAME, PASSWORD), authorizationRef.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveContentThrowsOnNonSuccessfulStatus() throws IOException {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(withCredentials(USERNAME, PASSWORD));
        HttpServer server = createServer(500, "{}", null);
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
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(withCredentials(USERNAME, PASSWORD));
        HttpServer server = createServer(200, "not-json", null);
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
    void resolveContentThrowsWhenCredentialsAreMissing() {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(withCredentials("", ""));

        StepVerifier.create(provider.resolveContent("http://localhost:8080"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    private HttpServer createServer(int status, String responseBody, AtomicReference<String> authorizationRef) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(PATH, exchange -> writeResponse(exchange, status, responseBody, authorizationRef));
        return server;
    }

    private String buildUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort() + PATH + "?" + QUERY;
    }

    private void writeResponse(HttpExchange exchange, int status, String responseBody,
                               AtomicReference<String> authorizationRef) throws IOException {
        if (authorizationRef != null) {
            authorizationRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
        }
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private RagProperties withCredentials(String username, String password) {
        RagProperties ragProperties = new RagProperties();
        ConfluenceProperties confluenceProperties = new ConfluenceProperties();
        confluenceProperties.setUsername(username);
        confluenceProperties.setPassword(password);
        ragProperties.setConfluence(confluenceProperties);
        return ragProperties;
    }

    private String expectedBasicAuth(String username, String password) {
        String token = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }
}
