package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.git.GitRepositoryInfoRq;
import io.github.admiralxy.agent.controller.response.git.GitRepositoryInfoRs;
import io.github.admiralxy.agent.service.git.GitRepositoryInfo;
import io.github.admiralxy.agent.service.git.GitRepositoryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitControllerTest {

    private final GitRepositoryService gitRepositoryService = Mockito.mock(GitRepositoryService.class);
    private final GitController gitController = new GitController(gitRepositoryService);

    @Test
    void getRepositoryInfoReturnsBranchesAndFolders() {
        GitRepositoryInfoRq rq = new GitRepositoryInfoRq("https://git.example/repo.git", "login", "password");
        Mockito.when(gitRepositoryService.getRepositoryInfo(rq.url(), rq.login(), rq.password()))
                .thenReturn(new GitRepositoryInfo(List.of("main", "develop"), List.of("src", "docs")));

        GitRepositoryInfoRs rs = gitController.getRepositoryInfo(rq);

        assertEquals(List.of("main", "develop"), rs.branches());
        assertEquals(List.of("src", "docs"), rs.folders());
    }
}
