package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.prompt.CreatePromptTemplateRq;
import io.github.admiralxy.agent.controller.request.prompt.UpdatePromptTemplateRq;
import io.github.admiralxy.agent.controller.response.prompt.CreatePromptTemplateRs;
import io.github.admiralxy.agent.controller.response.prompt.GetPromptTemplatesRs;
import io.github.admiralxy.agent.controller.response.prompt.UpdatePromptTemplateRs;
import io.github.admiralxy.agent.domain.PromptTemplate;
import io.github.admiralxy.agent.service.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

class PromptTemplateControllerTest {

    private final PromptTemplateService promptTemplateService = Mockito.mock(PromptTemplateService.class);
    private final PromptTemplateController promptTemplateController = new PromptTemplateController(promptTemplateService);

    @Test
    void getAllReturnsTemplates() {
        PromptTemplate template = new PromptTemplate(UUID.randomUUID(), "Default", "You are assistant");
        Mockito.when(promptTemplateService.getAll()).thenReturn(List.of(template));

        GetPromptTemplatesRs rs = promptTemplateController.getAll();

        assertEquals(1, rs.templates().size());
        assertEquals("Default", rs.templates().getFirst().name());
        assertEquals("You are assistant", rs.templates().getFirst().content());
    }

    @Test
    void createReturnsCreatedTemplate() {
        UUID id = UUID.randomUUID();
        CreatePromptTemplateRq rq = new CreatePromptTemplateRq("Code review", "Review this patch");
        Mockito.when(promptTemplateService.create(rq.name(), rq.content()))
                .thenReturn(new PromptTemplate(id, rq.name(), rq.content()));

        CreatePromptTemplateRs rs = promptTemplateController.create(rq);

        assertEquals(id, rs.id());
        assertEquals("Code review", rs.name());
        assertEquals("Review this patch", rs.content());
    }

    @Test
    void updateReturnsUpdatedTemplate() {
        UUID id = UUID.randomUUID();
        UpdatePromptTemplateRq rq = new UpdatePromptTemplateRq("Doc helper", "Summarize docs");
        Mockito.when(promptTemplateService.update(id, rq.name(), rq.content()))
                .thenReturn(new PromptTemplate(id, rq.name(), rq.content()));

        UpdatePromptTemplateRs rs = promptTemplateController.update(id, rq);

        assertEquals(id, rs.id());
        assertEquals("Doc helper", rs.name());
        assertEquals("Summarize docs", rs.content());
    }

    @Test
    void deleteDelegatesToService() {
        UUID id = UUID.randomUUID();

        promptTemplateController.delete(id);

        verify(promptTemplateService).delete(id);
    }
}
