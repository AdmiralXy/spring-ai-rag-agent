package io.github.admiralxy.agent.service.agent;

import io.github.admiralxy.agent.config.properties.AgentRunnerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@Slf4j
@Component
public class AgentRunnerClient {

    private static final String SECRET_HEADER = "x-runner-secret";
    private static final String CHAT_PATH = "/chats/{chatId}";
    private static final String QUERY_PATH = CHAT_PATH + "/query";
    private static final String SESSION_PATH = CHAT_PATH + "/session";
    private static final String FILES_PATH = CHAT_PATH + "/files";
    private static final String FILE_CONTENT_PATH = FILES_PATH + "/content";
    private static final String ARCHIVE_PATH = CHAT_PATH + "/archive";
    private static final String PATH_PARAM = "path";
    private static final String PATH_VARIABLE = "{path}";
    private static final String RESET_FAILED = "Failed to reset the agent session of chat {}: {}";
    private static final String DELETE_FAILED = "Failed to delete the agent workspace of chat {}: {}";
    private static final int MAX_EVENT_BYTES = 16 * 1024 * 1024;
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final AgentRunnerProperties properties;
    private final WebClient webClient;
    private final RestClient restClient;

    public AgentRunnerClient(AgentRunnerProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeaders(this::applySecret)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_EVENT_BYTES))
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .defaultHeaders(this::applySecret)
                .build();
    }

    public int maxTurns() {
        return properties.getMaxTurns();
    }

    public Flux<ServerSentEvent<String>> query(UUID chatId, AgentQuery query) {
        return webClient.post()
                .uri(QUERY_PATH, chatId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(query)
                .retrieve()
                .bodyToFlux(SSE_TYPE)
                .filter(event -> event.event() != null && event.data() != null);
    }

    public JsonNode listFiles(UUID chatId) {
        return restClient.get().uri(FILES_PATH, chatId).retrieve().body(JsonNode.class);
    }

    public ClientHttpResponse openFile(UUID chatId, String path) {
        return restClient.get()
                .uri(b -> b.path(FILE_CONTENT_PATH).queryParam(PATH_PARAM, PATH_VARIABLE).build(chatId, path))
                .exchange((request, response) -> response, false);
    }

    public ClientHttpResponse openArchive(UUID chatId) {
        return restClient.get().uri(ARCHIVE_PATH, chatId).exchange((request, response) -> response, false);
    }

    public void resetSession(UUID chatId) {
        try {
            restClient.delete().uri(SESSION_PATH, chatId).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.debug(RESET_FAILED, chatId, e.getMessage());
        }
    }

    public void deleteChat(UUID chatId) {
        try {
            restClient.delete().uri(CHAT_PATH, chatId).retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn(DELETE_FAILED, chatId, e.getMessage());
        }
    }

    private void applySecret(HttpHeaders headers) {
        if (StringUtils.isNotBlank(properties.getSecret())) {
            headers.set(SECRET_HEADER, properties.getSecret());
        }
    }
}
