package io.github.admiralxy.agent.config;

import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

@Configuration
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(EmbeddingModelRuntime embeddingModelRuntime) {
        return (EmbeddingModel) Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class[]{EmbeddingModel.class},
                (ignored, method, args) -> {
                    try {
                        return method.invoke(embeddingModelRuntime.getOrCreateModel(), args);
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                }
        );
    }
}
