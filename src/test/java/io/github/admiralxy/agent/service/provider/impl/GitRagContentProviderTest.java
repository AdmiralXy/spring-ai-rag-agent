package io.github.admiralxy.agent.service.provider.impl;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.ProviderType;
import io.github.admiralxy.agent.service.git.GitFile;
import io.github.admiralxy.agent.service.git.GitRepositoryService;
import io.github.admiralxy.agent.service.provider.RagChunk;
import io.github.admiralxy.agent.service.provider.RagContentRequest;
import io.github.admiralxy.agent.service.provider.RagGitOptions;
import io.github.admiralxy.agent.service.provider.RagProviderAuth;
import io.github.admiralxy.agent.service.rag.TextChunkerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class GitRagContentProviderTest {

    private final GitRepositoryService gitRepositoryService = Mockito.mock(GitRepositoryService.class);
    private final TextChunkerService chunker = Mockito.mock(TextChunkerService.class);
    private final GitRagContentProvider provider =
            new GitRagContentProvider(gitRepositoryService, chunker, new RagProperties());

    @Test
    void supportsGitOnly() {
        assertTrue(provider.supports(ProviderType.GIT));
        assertFalse(provider.supports(ProviderType.TEXT));
    }

    @Test
    void resolveChunksSplitsEveryFileAndTagsChunksWithThePath() {
        RagContentRequest request = new RagContentRequest(
                "https://git.example/repo.git",
                true,
                new RagGitOptions("main", "src"),
                new RagProviderAuth("login", "password")
        );
        Mockito.when(gitRepositoryService.getFiles(
                        request.text(),
                        request.git().branch(),
                        request.git().folder(),
                        request.auth().login(),
                        request.auth().password()
                ))
                .thenReturn(List.of(new GitFile("src/A.java", "a"), new GitFile("src/B.java", "b")));
        Mockito.when(chunker.chunk(eq("a"), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(List.of("a1", "a2"));
        Mockito.when(chunker.chunk(eq("b"), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(List.of("b1"));

        StepVerifier.create(provider.resolveChunks(request))
                .expectNext(new RagChunk("a1", 0, 3, "src/A.java", 0, 2))
                .expectNext(new RagChunk("a2", 1, 3, "src/A.java", 1, 2))
                .expectNext(new RagChunk("b1", 2, 3, "src/B.java", 0, 1))
                .verifyComplete();
    }
}
