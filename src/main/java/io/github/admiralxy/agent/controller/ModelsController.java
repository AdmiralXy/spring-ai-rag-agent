package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.response.chat.ModelInfoRs;
import io.github.admiralxy.agent.controller.response.chat.ModelsListRs;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/models")
public class ModelsController {

    private final ModelSettingsService modelSettingsService;

    @GetMapping
    public ModelsListRs getAll() {
        var models = modelSettingsService.getChatModels().stream()
                .map(model -> new ModelInfoRs(model.getLabel(), model.getId().toString()))
                .toList();

        return new ModelsListRs(models);
    }
}
