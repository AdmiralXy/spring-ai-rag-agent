package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.config.properties.AppProperties;
import io.github.admiralxy.agent.controller.response.chat.ModelInfoRs;
import io.github.admiralxy.agent.controller.response.chat.ModelsListRs;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/models")
public class ModelsController {

    private final AppProperties props;

    @GetMapping
    public ModelsListRs getAll() {
        var models = props.getModels().stream()
                .map(model -> new ModelInfoRs(model.getDisplayName(), model.getAlias()))
                .toList();

        return new ModelsListRs(models);
    }
}
