package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.prompt.CreatePromptTemplateRq;
import io.github.admiralxy.agent.controller.request.prompt.UpdatePromptTemplateRq;
import io.github.admiralxy.agent.controller.response.prompt.CreatePromptTemplateRs;
import io.github.admiralxy.agent.controller.response.prompt.GetPromptTemplatesRs;
import io.github.admiralxy.agent.controller.response.prompt.UpdatePromptTemplateRs;
import io.github.admiralxy.agent.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/prompt-templates")
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    @GetMapping
    public GetPromptTemplatesRs getAll() {
        return new GetPromptTemplatesRs(promptTemplateService.getAll());
    }

    @PostMapping
    public CreatePromptTemplateRs create(@RequestBody CreatePromptTemplateRq rq) {
        var template = promptTemplateService.create(rq.name(), rq.content());
        return new CreatePromptTemplateRs(template.id(), template.name(), template.content());
    }

    @PatchMapping("/{id}")
    public UpdatePromptTemplateRs update(@PathVariable UUID id, @RequestBody UpdatePromptTemplateRq rq) {
        var template = promptTemplateService.update(id, rq.name(), rq.content());
        return new UpdatePromptTemplateRs(template.id(), template.name(), template.content());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        promptTemplateService.delete(id);
    }
}
