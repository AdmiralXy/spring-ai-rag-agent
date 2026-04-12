package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.controller.response.documents.ProviderType;
import io.github.admiralxy.agent.service.git.GitRepositoryService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import io.github.admiralxy.agent.service.provider.RagGitOptions;
import io.github.admiralxy.agent.service.provider.RagProviderAuth;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitRagContentProviderTest {

    private final GitRepositoryService gitRepositoryService = Mockito.mock(GitRepositoryService.class);
    private final GitRagContentProvider provider = new GitRagContentProvider(gitRepositoryService);

    @Test
    void supportsGitOnly() {
        assertTrue(provider.supports(ProviderType.GIT));
        assertFalse(provider.supports(ProviderType.TEXT));
    }

    @Test
    void resolveChunksDelegatesToRepositoryService() {
        RagContentRequest request = new RagContentRequest(
                "https://git.example/repo.git",
                true,
                new RagGitOptions("main", "src"),
                new RagProviderAuth("login", "password")
        );
        Mockito.when(gitRepositoryService.getFileChunks(
                        request.text(),
                        request.git().branch(),
                        request.git().folder(),
                        request.auth().login(),
                        request.auth().password()
                ))
                .thenReturn(List.of("chunk-1", "chunk-2"));

        StepVerifier.create(provider.resolveChunks(request))
                .expectNext(new RagChunk("chunk-1", 0, 2))
                .expectNext(new RagChunk("chunk-2", 1, 2))
                .verifyComplete();
    }
}
