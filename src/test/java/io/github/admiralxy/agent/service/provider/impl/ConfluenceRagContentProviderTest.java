package io.github.admiralxy.agent.service.provider.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.TextChunkerService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import io.github.admiralxy.agent.service.provider.RagProviderAuth;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(noOpChunker(), clientFactory());
        assertTrue(provider.supports(ProviderType.CONFLUENCE));
        assertFalse(provider.supports(ProviderType.TEXT));
    }

    @Test
    void resolveContentReturnsTitleAndStorageValue() throws IOException {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(noOpChunker(), clientFactory());
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
            StepVerifier.create(provider.resolveChunks(request(buildUrl(server), USERNAME, PASSWORD)))
                    .expectNext(new RagChunk("Стенды\n\n<p>Контент</p>", 0, 1))
                    .verifyComplete();
            assertEquals(expectedBasicAuth(), authorizationRef.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveContentThrowsOnNonSuccessfulStatus() throws IOException {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(noOpChunker(), clientFactory());
        HttpServer server = createServer(500, "{}", null);
        server.start();

        try {
            StepVerifier.create(provider.resolveChunks(request(buildUrl(server), USERNAME, PASSWORD)))
                    .expectError(IllegalStateException.class)
                    .verify();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveContentThrowsOnInvalidJson() throws IOException {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(noOpChunker(), clientFactory());
        HttpServer server = createServer(200, "not-json", null);
        server.start();

        try {
            StepVerifier.create(provider.resolveChunks(request(buildUrl(server), USERNAME, PASSWORD)))
                    .expectError(IllegalStateException.class)
                    .verify();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveContentThrowsWhenCredentialsAreMissing() {
        ConfluenceRagContentProvider provider = new ConfluenceRagContentProvider(noOpChunker(), clientFactory());

        StepVerifier.create(provider.resolveChunks(request("http://localhost:8080", "", "")))
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

    private RagContentRequest request(String url, String login, String password) {
        return new RagContentRequest(url, false, null, new RagProviderAuth(login, password));
    }

    private TextChunkerService noOpChunker() {
        TextChunkerService chunker = Mockito.mock(TextChunkerService.class);
        Mockito.when(chunker.chunk(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(java.util.List.of());
        return chunker;
    }

    private String expectedBasicAuth() {
        String token = Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private AiHttpClientBuilderFactory clientFactory() {
        return new AiHttpClientBuilderFactory(false);
    }
}
