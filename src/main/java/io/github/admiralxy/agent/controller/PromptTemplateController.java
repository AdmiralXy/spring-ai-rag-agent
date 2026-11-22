package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.prompt.CreatePromptTemplateRq;
import io.github.admiralxy.agent.controller.request.prompt.UpdatePromptTemplateRq;
import io.github.admiralxy.agent.controller.response.prompt.CreatePromptTemplateRs;
import io.github.admiralxy.agent.controller.response.prompt.GetPromptTemplatesRs;
import io.github.admiralxy.agent.controller.response.prompt.UpdatePromptTemplateRs;
import io.github.admiralxy.agent.security.AuthUser;
import io.github.admiralxy.agent.service.prompt.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public GetPromptTemplatesRs getAll(@AuthenticationPrincipal AuthUser user) {
        return new GetPromptTemplatesRs(promptTemplateService.getAll(user.id()));
    }

    @PostMapping
    public CreatePromptTemplateRs create(@AuthenticationPrincipal AuthUser user, @RequestBody CreatePromptTemplateRq rq) {
        var template = promptTemplateService.create(user.id(), rq.name(), rq.content());
        return new CreatePromptTemplateRs(template.id(), template.name(), template.content());
    }

    @PatchMapping("/{id}")
    public UpdatePromptTemplateRs update(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                         @RequestBody UpdatePromptTemplateRq rq) {
        var template = promptTemplateService.update(user.id(), id, rq.name(), rq.content());
        return new UpdatePromptTemplateRs(template.id(), template.name(), template.content());
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        promptTemplateService.delete(user.id(), id);
    }
}
