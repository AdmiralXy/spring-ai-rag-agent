package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.chat.GetDocumentsRs;
import io.github.admiralxy.agent.controller.request.documents.AddToSpaceRs;
import io.github.admiralxy.agent.controller.response.documents.AddToSpaceRq;
import io.github.admiralxy.agent.domain.RagDocument;
import io.github.admiralxy.agent.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rag")
public class RagController {

    private final RagService ragService;

    @PostMapping("/{space}/documents")
    public AddToSpaceRs add(@PathVariable String space, @RequestBody AddToSpaceRq rq) {
        return new AddToSpaceRs(ragService.add(space, rq.text()));
    }

    @DeleteMapping("/{space}/documents/{docId}")
    public void delete(@PathVariable String space, @PathVariable String docId) {
        ragService.deleteFromSpace(space, docId);
    }

    @GetMapping("/{space}/search")
    public GetDocumentsRs search(
            @PathVariable String space,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int k
    ) {
        List<RagDocument> documents = ragService.search(space, q, k).stream()
                .map(d -> new RagDocument(d.getId(), d.getContent(), d.getMetadata()))
                .toList();
        return new GetDocumentsRs(documents);
    }

    @GetMapping("/{space}/documents")
    public GetDocumentsRs listDocuments(
            @PathVariable String space,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<RagDocument> documents = ragService.listDocuments(space, limit).stream()
                .map(d -> new RagDocument(d.getId(),
                        d.getContent().substring(0, Math.min(3000, d.getContent().length())) + "...",
                        d.getMetadata()))
                .toList();

        return new GetDocumentsRs(documents);
    }
}
