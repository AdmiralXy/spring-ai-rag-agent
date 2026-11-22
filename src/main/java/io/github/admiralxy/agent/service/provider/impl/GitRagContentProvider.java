package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.service.git.GitFile;
import io.github.admiralxy.agent.service.git.GitRepositoryService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentProvider;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import io.github.admiralxy.agent.service.rag.TextChunkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GitRagContentProvider implements RagContentProvider {

    private final GitRepositoryService gitRepositoryService;
    private final TextChunkerService textChunkerService;
    private final RagProperties ragProperties;

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
            List<GitFile> files = gitRepositoryService.getFiles(request.text(), branch, folder, login, password);
            List<RagChunk> chunks = new ArrayList<>();
            RagProperties.Chunking chunking = ragProperties.getChunking();
            for (GitFile file : files) {
                List<String> parts = textChunkerService.chunk(file.content(), chunking.getMaxLines(),
                        chunking.getMaxChars(), chunking.getOverlapLines(), chunking.getOverlapWords());
                for (int i = 0; i < parts.size(); i++) {
                    chunks.add(new RagChunk(parts.get(i), 0, 0, file.path(), i, parts.size()));
                }
            }
            int total = chunks.size();
            return Flux.range(0, total).map(i -> {
                RagChunk c = chunks.get(i);
                return new RagChunk(c.text(), i, total, c.source(), c.part(), c.parts());
            });
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
