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

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class ConfluenceRagContentProvider extends AbstractChunkingRagContentProvider {

    private static final String REQUEST_HEADER_ACCEPT = "Accept";
    private static final String REQUEST_HEADER_ACCEPT_VALUE = "application/json";
    private static final String TITLE_FIELD = "title";
    private static final String BODY_FIELD = "body";
    private static final String STORAGE_FIELD = "storage";
    private static final String VALUE_FIELD = "value";
    private static final String VIEW_PAGE_PATH = "/pages/viewpage.action";
    private static final String CONTENT_API_PATH_TEMPLATE = "/rest/api/content/%s";
    private static final String CONTENT_API_QUERY = "expand=body.storage";

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
        String url = toConfluenceApiUrl(request.text());
        String username = request.auth() == null ? null : request.auth().login();
        String password = request.auth() == null ? null : request.auth().password();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return Mono.error(new IllegalStateException("Confluence credentials are required"));
        }

        return webClient.get()
                .uri(url)
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

    private String toConfluenceApiUrl(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid Confluence URL", e);
        }

        String pageId = extractQueryParam(uri.getRawQuery(), "pageId");
        if (StringUtils.isBlank(pageId)) {
            throw new IllegalStateException("Confluence pageId is required in URL query");
        }

        if (!VIEW_PAGE_PATH.equals(uri.getPath())) {
            throw new IllegalStateException("Confluence URL path must be /pages/viewpage.action");
        }

        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    CONTENT_API_PATH_TEMPLATE.formatted(pageId),
                    CONTENT_API_QUERY,
                    null
            ).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to build Confluence REST API URL", e);
        }
    }

    private String extractQueryParam(String query, String name) {
        if (StringUtils.isBlank(query)) {
            return null;
        }
        String prefix = name + "=";
        for (String part : query.split("&")) {
            if (part.startsWith(prefix) && part.length() > prefix.length()) {
                return part.substring(prefix.length());
            }
        }
        return null;
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
