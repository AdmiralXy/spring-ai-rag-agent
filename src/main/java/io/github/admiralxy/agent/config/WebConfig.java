package io.github.admiralxy.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

    private static final String ALL_PATHS = "/**";
    private static final String ANY = "*";
    private static final String[] METHODS = {
            HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(ALL_PATHS)
                .allowedOriginPatterns(ANY)
                .allowedMethods(METHODS)
                .allowedHeaders(ANY)
                .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION);
    }
}
