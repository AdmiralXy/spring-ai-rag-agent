package io.github.admiralxy.agent.config;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionAdvisors;
import org.springframework.ai.session.jdbc.JdbcSessionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;

@Configuration
public class SessionConfig {

    private static final Duration SESSION_TTL = Duration.ofDays(365L * 100);

    @Bean
    public SessionRepository sessionRepository(DataSource dataSource, JsonMapper jsonMapper) {
        return JdbcSessionRepository.builder()
                .dataSource(dataSource)
                .jsonMapper(jsonMapper)
                .build();
    }

    @Bean
    public SessionService sessionService(SessionRepository sessionRepository) {
        return DefaultSessionService.builder()
                .sessionRepository(sessionRepository)
                .defaultTimeToLive(SESSION_TTL)
                .build();
    }

    @Bean
    public SessionChatAdvisors sessionChatAdvisors(SessionService sessionService) {
        return new SessionChatAdvisors(SessionAdvisors.builder(sessionService).build());
    }

    public record SessionChatAdvisors(List<Advisor> advisors) {
    }
}
