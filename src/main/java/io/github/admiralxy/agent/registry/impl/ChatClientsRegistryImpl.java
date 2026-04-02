package io.github.admiralxy.agent.registry.impl;

import io.github.admiralxy.agent.config.properties.AppProperties;
import io.github.admiralxy.agent.config.properties.ModelProperties;
import io.github.admiralxy.agent.config.properties.ModelsProperties;
import io.github.admiralxy.agent.registry.ChatClientsRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class ChatClientsRegistryImpl implements ChatClientsRegistry {

    private final Map<String, ChatClient> chatClients;
    private final AppProperties props;

    @Override
    public boolean contains(String alias) {
        return chatClients.containsKey(alias);
    }

    @Override
    public ChatClient getChatClient(String alias) {
        return chatClients.get(alias);
    }

    @Override
    public ModelProperties getProperties(String alias) {
        return props.getModels().stream()
                .filter(modelsProperties -> alias.equals(modelsProperties.getAlias()))
                .findFirst()
                .map(ModelsProperties::getProperties)
                .orElseThrow();
    }

    @Override
    public Optional<String> getSummarizerAlias() {
        List<String> summarizers = props.getModels().stream()
                .filter(ModelsProperties::isSummarizer)
                .map(ModelsProperties::getAlias)
                .filter(chatClients::containsKey)
                .toList();

        if (!summarizers.isEmpty()) {
            int idx = ThreadLocalRandom.current().nextInt(summarizers.size());
            return Optional.of(summarizers.get(idx));
        }

        List<String> aliases = new ArrayList<>(chatClients.keySet());
        if (aliases.isEmpty()) {
            return Optional.empty();
        }

        int idx = ThreadLocalRandom.current().nextInt(aliases.size());
        return Optional.of(aliases.get(idx));
    }
}
