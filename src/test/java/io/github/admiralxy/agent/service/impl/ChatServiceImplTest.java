package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.ChatProperties;
import io.github.admiralxy.agent.config.properties.ModelProperties;
import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.Chat;
import io.github.admiralxy.agent.domain.ChatMessage;
import io.github.admiralxy.agent.entity.ConversationEntity;
import io.github.admiralxy.agent.registry.ChatClientsRegistry;
import io.github.admiralxy.agent.repository.ConversationRepository;
import io.github.admiralxy.agent.service.RagService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private RagService ragService;
    @Mock
    private ChatProperties chatProperties;
    @Mock
    private RagProperties ragProperties;
    @Mock
    private ChatClientsRegistry chatClientsRegistry;
    @Mock
    private ChatMemory chatMemory;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Test
    void getAll_shouldReturnPageOfChats() {
        UUID id = UUID.randomUUID();
        ConversationEntity entity = new ConversationEntity();
        entity.setId(id);
        entity.setTitle("test-title");
        entity.setModelName("gpt-4");
        entity.setRagSpace("space1");

        Page<ConversationEntity> page = new PageImpl<>(List.of(entity));
        when(conversationRepository.findAll(any(Pageable.class))).thenReturn(page);

        Page<Chat> result = chatService.getAll(10);

        assertThat(result.getContent()).hasSize(1);
        Chat chat = result.getContent().getFirst();
        assertThat(chat.id()).isEqualTo(id);
        assertThat(chat.title()).isEqualTo("test-title");
        assertThat(chat.modelName()).isEqualTo("gpt-4");
        assertThat(chat.ragSpace()).isEqualTo("space1");

        verify(conversationRepository).findAll(any(Pageable.class));
    }

    @Test
    void create_shouldSaveConversationAndReturnIdAndTitle() {
        String ragSpace = "my-space";

        when(conversationRepository.save(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        Pair<UUID, String> result = chatService.create(ragSpace);

        assertThat(result.getLeft()).isNotNull();
        assertThat(result.getRight()).isNotBlank();

        ArgumentCaptor<ConversationEntity> captor = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(captor.capture());

        ConversationEntity captured = captor.getValue();
        assertThat(captured.getRagSpace()).isEqualTo(ragSpace);
        assertThat(captured.getTitle()).isNotBlank();
    }

    @Test
    void updateModelName_shouldUpdateAndReturnModelName() {
        UUID chatId = UUID.randomUUID();
        String modelName = "gpt-4o";

        ConversationEntity entity = new ConversationEntity();
        entity.setId(chatId);
        entity.setModelName("old-model");

        when(conversationRepository.findById(chatId)).thenReturn(Optional.of(entity));
        when(conversationRepository.save(any(ConversationEntity.class))).thenReturn(entity);

        String result = chatService.updateModelName(chatId, modelName);

        assertThat(result).isEqualTo(modelName);
        assertThat(entity.getModelName()).isEqualTo(modelName);
        verify(conversationRepository).save(entity);
    }

    @Test
    void updateModelName_shouldThrowWhenConversationNotFound() {
        UUID chatId = UUID.randomUUID();
        when(conversationRepository.findById(chatId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.updateModelName(chatId, "model"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void send_shouldThrowWhenModelNotFound() {
        UUID id = UUID.randomUUID();
        String modelAlias = "unknown-model";

        when(chatClientsRegistry.contains(modelAlias)).thenReturn(false);

        assertThatThrownBy(() -> chatService.send(id, modelAlias, "hello"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void send_shouldThrowWhenConversationNotFound() {
        UUID id = UUID.randomUUID();
        String modelAlias = "gpt-4";

        when(chatClientsRegistry.contains(modelAlias)).thenReturn(true);

        ChatClient chatClient = mock(ChatClient.class);
        when(chatClientsRegistry.getChatClient(modelAlias)).thenReturn(chatClient);

        ModelProperties props = mock(ModelProperties.class);
        when(chatClientsRegistry.getProperties(modelAlias)).thenReturn(props);

        when(conversationRepository.findById(id)).thenReturn(Optional.empty());

        Flux<String> result = chatService.send(id, modelAlias, "hello");

        StepVerifier.create(result)
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void send_shouldStreamResponseForStreamingModel() {
        UUID id = UUID.randomUUID();
        String modelAlias = "gpt-4";
        String userText = "hello";

        when(chatClientsRegistry.contains(modelAlias)).thenReturn(true);

        ModelProperties props = mock(ModelProperties.class);
        when(props.getMaxContextTokens()).thenReturn(4000);
        when(props.isStreaming()).thenReturn(true);
        when(props.getSystemPrompt()).thenReturn("You are helpful.");
        when(chatClientsRegistry.getProperties(modelAlias)).thenReturn(props);

        when(ragProperties.getPercentage()).thenReturn((int) 0.5);
        when(ragProperties.getTopK()).thenReturn(5);

        ConversationEntity entity = new ConversationEntity();
        entity.setId(id);
        entity.setRagSpace("space");
        when(conversationRepository.findById(id)).thenReturn(Optional.of(entity));

        when(ragService.buildContext(eq("space"), eq(userText), anyDouble(), anyInt(), anyInt()))
                .thenReturn("some context");

        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        when(chatClientsRegistry.getChatClient(modelAlias)).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("Hello", " World"));

        // Prevent duplicate check from failing
        when(chatMemory.get(eq(id.toString()), eq(1))).thenReturn(List.of());

        Flux<String> result = chatService.send(id, modelAlias, userText);

        StepVerifier.create(result)
                .expectNext("Hello")
                .expectNext("Hello World")
                .verifyComplete();
    }

    @Test
    void send_shouldCallContentForNonStreamingModel() {
        UUID id = UUID.randomUUID();
        String modelAlias = "gpt-4";
        String userText = "hello";

        when(chatClientsRegistry.contains(modelAlias)).thenReturn(true);

        ModelProperties props = mock(ModelProperties.class);
        when(props.getMaxContextTokens()).thenReturn(4000);
        when(props.isStreaming()).thenReturn(false);
        when(props.getSystemPrompt()).thenReturn(null);
        when(chatClientsRegistry.getProperties(modelAlias)).thenReturn(props);

        when(ragProperties.getPercentage()).thenReturn((int) 0.5);
        when(ragProperties.getTopK()).thenReturn(5);

        ConversationEntity entity = new ConversationEntity();
        entity.setId(id);
        entity.setRagSpace(null);
        when(conversationRepository.findById(id)).thenReturn(Optional.of(entity));

        when(ragService.buildContext(any(), eq(userText), anyDouble(), anyInt(), anyInt()))
                .thenReturn(null);

        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientsRegistry.getChatClient(modelAlias)).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Full response");

        when(chatMemory.get(eq(id.toString()), eq(1))).thenReturn(List.of());

        Flux<String> result = chatService.send(id, modelAlias, userText);

        StepVerifier.create(result)
                .expectNext("Full response")
                .verifyComplete();
    }

    @Test
    void history_shouldReturnChatMessages() {
        UUID id = UUID.randomUUID();
        int historyLimit = 50;

        when(chatProperties.getHistoryLimit()).thenReturn(historyLimit);

        List<Message> messages = List.of(
                new UserMessage("hi"),
                new AssistantMessage("hello")
        );
        when(chatMemory.get(id.toString(), historyLimit)).thenReturn(messages);

        List<ChatMessage> result = chatService.history(id);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).role()).isEqualTo("USER");
        assertThat(result.get(0).content()).isEqualTo("hi");
        assertThat(result.get(1).role()).isEqualTo("ASSISTANT");
        assertThat(result.get(1).content()).isEqualTo("hello");
    }

    @Test
    void delete_shouldDeleteConversationAndClearMemory() {
        UUID chatId = UUID.randomUUID();
        when(conversationRepository.existsById(chatId)).thenReturn(true);

        chatService.delete(chatId);

        verify(conversationRepository).deleteById(chatId);
        verify(chatMemory).clear(chatId.toString());
    }

    @Test
    void delete_shouldThrowWhenConversationNotFound() {
        UUID chatId = UUID.randomUUID();
        when(conversationRepository.existsById(chatId)).thenReturn(false);

        assertThatThrownBy(() -> chatService.delete(chatId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversation not found");

        verify(conversationRepository, never()).deleteById(any());
        verify(chatMemory, never()).clear(anyString());
    }
}
