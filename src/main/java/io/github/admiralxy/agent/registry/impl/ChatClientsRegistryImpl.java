package io.github.admiralxy.agent.registry.impl;

import io.github.admiralxy.agent.entity.ChatModelSettingsEntity;
import io.github.admiralxy.agent.entity.SummarizerModelSettingsEntity;
import io.github.admiralxy.agent.registry.ChatClientsRegistry;
import io.github.admiralxy.agent.registry.ChatModelRuntimeProperties;
import io.github.admiralxy.agent.repository.ChatModelSettingsRepository;
import io.github.admiralxy.agent.repository.SummarizerModelSettingsRepository;
import io.github.admiralxy.agent.service.model.ChatModelClientFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatClientsRegistryImpl implements ChatClientsRegistry {

    private static final short SUMMARIZER_SINGLETON_ID = 1;

    private final ChatModelSettingsRepository chatModelRepository;
    private final SummarizerModelSettingsRepository summarizerRepository;
    private final ChatModelClientFactory chatModelClientFactory;
    private final ChatMemory chatMemory;

    @Override
    public boolean contains(String modelId) {
        return resolveChatModel(modelId).isPresent();
    }

    @Override
    public ChatClient getChatClient(String modelId) {
        ChatModelSettingsEntity entity = resolveChatModel(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Chat model not found: " + modelId));

        return chatModelClientFactory.createWithMemory(
                entity.getProvider(),
                entity.getName(),
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.isStreaming(),
                entity.getTemperature(),
                chatMemory
        );
    }

    @Override
    public ChatModelRuntimeProperties getProperties(String modelId) {
        ChatModelSettingsEntity entity = resolveChatModel(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Chat model not found: " + modelId));

        return new ChatModelRuntimeProperties(
                entity.isStreaming(),
                entity.getMaxContextTokens(),
                entity.getSystemPrompt()
        );
    }

    @Override
    public Optional<ChatClient> getSummarizerClient() {
        return summarizerRepository.findById(SUMMARIZER_SINGLETON_ID)
                .map(entity -> chatModelClientFactory.create(
                        entity.getProvider(),
                        entity.getName(),
                        entity.getBaseUrl(),
                        entity.getApiKey(),
                        false,
                        0.0
                ));
    }

    @Override
    public Optional<String> getSummarizerSystemPrompt() {
        return summarizerRepository.findById(SUMMARIZER_SINGLETON_ID)
                .map(SummarizerModelSettingsEntity::getSystemPrompt);
    }

    private Optional<ChatModelSettingsEntity> resolveChatModel(String modelId) {
        try {
            UUID id = UUID.fromString(modelId);
            return chatModelRepository.findById(id);
        } catch (IllegalArgumentException ignored) {
            return chatModelRepository.findFirstByNameIgnoreCase(modelId)
                    .or(() -> chatModelRepository.findFirstByLabelIgnoreCase(modelId));
        }
    }
}
