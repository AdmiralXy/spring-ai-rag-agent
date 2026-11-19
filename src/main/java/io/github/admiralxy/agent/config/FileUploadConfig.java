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

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FilterRegistrationBean<RequestBodyLimitFilter> uploadRequestBodyLimitFilter(FilesProperties properties) {
        long maxBytes = properties.getUploads().getMaxRequestSize().toBytes();
        FilterRegistrationBean<RequestBodyLimitFilter> registration = new FilterRegistrationBean<>(new RequestBodyLimitFilter(maxBytes));
        registration.addUrlPatterns("/files/uploads", "/files/uploads/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
