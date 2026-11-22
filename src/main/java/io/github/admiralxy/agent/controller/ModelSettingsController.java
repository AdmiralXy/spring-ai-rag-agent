package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.mapper.ModelSettingsMapper;
import io.github.admiralxy.agent.controller.request.settings.ChatModelUpsertRq;
import io.github.admiralxy.agent.controller.request.settings.EmbeddingsModelUpdateRq;
import io.github.admiralxy.agent.controller.request.settings.SummarizerModelUpdateRq;
import io.github.admiralxy.agent.controller.response.settings.ChatModelRs;
import io.github.admiralxy.agent.controller.response.settings.ChatModelsRs;
import io.github.admiralxy.agent.controller.response.settings.EmbeddingsModelRs;
import io.github.admiralxy.agent.controller.response.settings.SummarizerModelRs;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/settings/models")
public class ModelSettingsController {

    private final ModelSettingsService modelSettingsService;
    private final ModelSettingsMapper mapper;

    @GetMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify() {
    }

    @GetMapping("/chat")
    public ChatModelsRs getChatModels() {
        return new ChatModelsRs(modelSettingsService.getChatModels().stream().map(mapper::toRs).toList());
    }

    @PostMapping("/chat")
    public ChatModelRs createChatModel(@RequestBody ChatModelUpsertRq rq) {
        return mapper.toRs(modelSettingsService.createChatModel(entity -> mapper.apply(entity, rq)));
    }

    @PutMapping("/chat/{id}")
    public ChatModelRs updateChatModel(@PathVariable UUID id, @RequestBody ChatModelUpsertRq rq) {
        return mapper.toRs(modelSettingsService.updateChatModel(id, entity -> mapper.apply(entity, rq)));
    }

    @DeleteMapping("/chat/{id}")
    public void deleteChatModel(@PathVariable UUID id) {
        modelSettingsService.deleteChatModel(id);
    }

    @GetMapping("/embeddings")
    public EmbeddingsModelRs getEmbeddingsModel() {
        return mapper.toRs(modelSettingsService.getEmbeddingModel());
    }

    @PutMapping("/embeddings")
    public EmbeddingsModelRs updateEmbeddingsModel(@RequestBody EmbeddingsModelUpdateRq rq) {
        return mapper.toRs(modelSettingsService.updateEmbeddingModel(entity -> mapper.apply(entity, rq)));
    }

    @GetMapping("/summarizer")
    public SummarizerModelRs getSummarizerModel() {
        return mapper.toRs(modelSettingsService.getSummarizerModel());
    }

    @PutMapping("/summarizer")
    public SummarizerModelRs updateSummarizerModel(@RequestBody SummarizerModelUpdateRq rq) {
        return mapper.toRs(modelSettingsService.updateSummarizerModel(entity -> mapper.apply(entity, rq)));
    }
}
