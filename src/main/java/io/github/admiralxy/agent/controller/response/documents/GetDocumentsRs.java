package io.github.admiralxy.agent.controller.response.documents;

import io.github.admiralxy.agent.domain.RagDocument;

import java.util.List;

public record GetDocumentsRs(List<RagDocument> documents) {
}
