package io.github.admiralxy.agent.config;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.web.RequestBodyLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class FileUploadConfig {

    private static final String UPLOADS_PATH = "/files/uploads";
    private static final String UPLOADS_SUBPATHS = UPLOADS_PATH + "/*";
    private static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FilterRegistrationBean<RequestBodyLimitFilter> uploadRequestBodyLimitFilter(FilesProperties properties) {
        long maxBytes = properties.getUploads().getMaxRequestSize().toBytes();
        FilterRegistrationBean<RequestBodyLimitFilter> registration =
                new FilterRegistrationBean<>(new RequestBodyLimitFilter(maxBytes));
        registration.addUrlPatterns(UPLOADS_PATH, UPLOADS_SUBPATHS);
        registration.setOrder(FILTER_ORDER);
        return registration;
    }
}
