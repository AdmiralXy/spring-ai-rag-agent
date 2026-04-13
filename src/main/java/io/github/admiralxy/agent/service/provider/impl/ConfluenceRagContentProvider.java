package io.github.admiralxy.agent.service.provider.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.TextChunkerService;
import io.github.admiralxy.agent.service.provider.AbstractChunkingRagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class ConfluenceRagContentProvider extends AbstractChunkingRagContentProvider {

    private static final String REQUEST_HEADER_ACCEPT = "Accept";
    private static final String REQUEST_HEADER_ACCEPT_VALUE = "application/json";
    private static final String TITLE_FIELD = "title";
    private static final String BODY_FIELD = "body";
    private static final String STORAGE_FIELD = "storage";
    private static final String VALUE_FIELD = "value";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public ConfluenceRagContentProvider(TextChunkerService textChunkerService,
                                        AiHttpClientBuilderFactory httpClientBuilderFactory) {
        super(textChunkerService);
        this.webClient = httpClientBuilderFactory.createWebClientBuilder().build();
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.CONFLUENCE == providerType;
    }

    @Override
    protected Mono<String> resolveContent(RagContentRequest request) {
        String username = request.auth() == null ? null : request.auth().login();
        String password = request.auth() == null ? null : request.auth().password();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return Mono.error(new IllegalStateException("Confluence credentials are required"));
        }

        return webClient.get()
                .uri(request.text())
                .accept(MediaType.APPLICATION_JSON)
                .header(REQUEST_HEADER_ACCEPT, REQUEST_HEADER_ACCEPT_VALUE)
                .headers(headers -> headers.setBasicAuth(username, password))
                .retrieve()
                .onStatus(status -> status.value() >= 400,
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty(StringUtils.EMPTY)
                                .map(ignored -> new IllegalStateException(
                                        "Confluence request failed with status: " + response.statusCode().value())))
                .bodyToMono(String.class)
                .flatMap(this::parseContent);
    }

    private Mono<String> parseContent(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("Failed to parse Confluence response", e));
        }

        String title = root.path(TITLE_FIELD).asText(StringUtils.EMPTY);
        String value = root.path(BODY_FIELD)
                .path(STORAGE_FIELD)
                .path(VALUE_FIELD)
                .asText(StringUtils.EMPTY);

        if (StringUtils.isBlank(title)) {
            return Mono.just(value);
        }
        if (StringUtils.isBlank(value)) {
            return Mono.just(title);
        }
        return Mono.just(title + "\n\n" + value);
    }
}
