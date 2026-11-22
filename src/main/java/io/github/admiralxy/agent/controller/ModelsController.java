package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.mapper.ModelSettingsMapper;
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
    private final ModelSettingsMapper mapper;

    @GetMapping
    public ModelsListRs getAll() {
        return new ModelsListRs(modelSettingsService.getChatModels().stream().map(mapper::toInfo).toList());
    }
}
