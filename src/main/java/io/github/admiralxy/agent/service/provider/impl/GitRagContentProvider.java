package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.git.GitRepositoryService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class GitRagContentProvider implements RagContentProvider {

    private final GitRepositoryService gitRepositoryService;

    @Override
    public boolean supports(ProviderType providerType) {
        return ProviderType.GIT == providerType;
    }

    @Override
    public Flux<RagChunk> resolveChunks(RagContentRequest request) {
        String branch = request.git() == null ? null : request.git().branch();
        String folder = request.git() == null ? null : request.git().folder();
        String login = request.auth() == null ? null : request.auth().login();
        String password = request.auth() == null ? null : request.auth().password();

        return Flux.defer(() -> {
            var chunks = gitRepositoryService.getFileChunks(
                    request.text(),
                    branch,
                    folder,
                    login,
                    password
            );
            int total = chunks.size();
            return Flux.range(0, total).map(i -> new RagChunk(chunks.get(i), i, total));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
