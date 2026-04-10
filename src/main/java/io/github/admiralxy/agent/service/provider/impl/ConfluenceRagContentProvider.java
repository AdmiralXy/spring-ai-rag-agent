package io.github.admiralxy.agent.service.provider.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ConfluenceRagContentProvider implements RagContentProvider {

    private static final String REQUEST_HEADER_ACCEPT = "Accept";
    private static final String REQUEST_HEADER_ACCEPT_VALUE = "application/json";
    private static final String TITLE_FIELD = "title";
    private static final String BODY_FIELD = "body";
    private static final String STORAGE_FIELD = "storage";
    private static final String VALUE_FIELD = "value";

    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder().build();

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.CONFLUENCE == providerType;
    }

    @Override
    public Mono<String> resolveContent(String text) {
        String username = ragProperties.getConfluence().getUsername();
        String password = ragProperties.getConfluence().getPassword();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return Mono.error(new IllegalStateException("Confluence basic auth credentials are not configured"));
        }

        return webClient.get()
                .uri(text)
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
