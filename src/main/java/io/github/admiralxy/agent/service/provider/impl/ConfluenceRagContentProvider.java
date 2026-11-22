package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.service.provider.AbstractChunkingRagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContent;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import io.github.admiralxy.agent.service.rag.TextChunkerService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URISyntaxException;

@Component
public class ConfluenceRagContentProvider extends AbstractChunkingRagContentProvider {

    private static final String TITLE_FIELD = "title";
    private static final String BODY_FIELD = "body";
    private static final String STORAGE_FIELD = "storage";
    private static final String VALUE_FIELD = "value";
    private static final String PAGE_ID_PARAM = "pageId";
    private static final String QUERY_SEPARATOR = "&";
    private static final String PARAM_FORMAT = "%s=";
    private static final String PARAGRAPH = "\n\n";
    private static final String VIEW_PAGE_PATH = "/pages/viewpage.action";
    private static final String CONTENT_API_PATH = "/rest/api/content/%s";
    private static final String CONTENT_API_QUERY = "expand=body.storage";
    private static final int ERROR_STATUS = 400;
    private static final String CREDENTIALS_REQUIRED = "Confluence credentials are required";
    private static final String REQUEST_FAILED = "Confluence request failed with status: %d";
    private static final String INVALID_URL = "Invalid Confluence URL";
    private static final String PAGE_ID_REQUIRED = "Confluence pageId is required in URL query";
    private static final String PATH_INVALID = "Confluence URL path must be " + VIEW_PAGE_PATH;
    private static final String API_URL_FAILED = "Failed to build Confluence REST API URL";
    private static final String PARSE_FAILED = "Failed to parse Confluence response";

    private final WebClient webClient;
    private final JsonMapper jsonMapper;
    private final ConfluenceStorageHtmlFormatter htmlFormatter = new ConfluenceStorageHtmlFormatter();

    public ConfluenceRagContentProvider(TextChunkerService textChunkerService,
                                        RagProperties ragProperties,
                                        AiHttpClientBuilderFactory httpClientBuilderFactory,
                                        JsonMapper jsonMapper) {
        super(textChunkerService, ragProperties);
        this.webClient = httpClientBuilderFactory.createWebClientBuilder().build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.CONFLUENCE == providerType;
    }

    @Override
    protected Mono<RagContent> resolveContent(RagContentRequest request) {
        String url = toApiUrl(request.text());
        String username = request.auth() == null ? null : request.auth().login();
        String password = request.auth() == null ? null : request.auth().password();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            return Mono.error(new IllegalStateException(CREDENTIALS_REQUIRED));
        }
        return webClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBasicAuth(username, password))
                .retrieve()
                .onStatus(status -> status.value() >= ERROR_STATUS,
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty(StringUtils.EMPTY)
                                .map(ignored -> new IllegalStateException(
                                        REQUEST_FAILED.formatted(response.statusCode().value()))))
                .bodyToMono(String.class)
                .flatMap(this::parseContent);
    }

    private String toApiUrl(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(INVALID_URL, e);
        }
        String pageId = queryParam(uri.getRawQuery(), PAGE_ID_PARAM);
        if (StringUtils.isBlank(pageId)) {
            throw new IllegalStateException(PAGE_ID_REQUIRED);
        }
        if (!VIEW_PAGE_PATH.equals(uri.getPath())) {
            throw new IllegalStateException(PATH_INVALID);
        }
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
                    CONTENT_API_PATH.formatted(pageId), CONTENT_API_QUERY, null).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(API_URL_FAILED, e);
        }
    }

    private static String queryParam(String query, String name) {
        if (StringUtils.isBlank(query)) {
            return null;
        }
        String prefix = PARAM_FORMAT.formatted(name);
        for (String part : query.split(QUERY_SEPARATOR)) {
            if (part.startsWith(prefix) && part.length() > prefix.length()) {
                return part.substring(prefix.length());
            }
        }
        return null;
    }

    private Mono<RagContent> parseContent(String body) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (RuntimeException e) {
            return Mono.error(new IllegalStateException(PARSE_FAILED, e));
        }
        String title = root.path(TITLE_FIELD).asString(StringUtils.EMPTY);
        String html = root.path(BODY_FIELD).path(STORAGE_FIELD).path(VALUE_FIELD).asString(StringUtils.EMPTY);
        String text = htmlFormatter.format(html);
        if (StringUtils.isBlank(title)) {
            return Mono.just(new RagContent(null, text));
        }
        if (StringUtils.isBlank(text)) {
            return Mono.just(new RagContent(title, title));
        }
        return Mono.just(new RagContent(title, title + PARAGRAPH + text));
    }
}
