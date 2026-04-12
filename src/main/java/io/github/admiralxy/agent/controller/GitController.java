package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.git.GitRepositoryInfoRq;
import io.github.admiralxy.agent.controller.response.git.GitRepositoryInfoRs;
import io.github.admiralxy.agent.service.git.GitRepositoryInfo;
import io.github.admiralxy.agent.service.git.GitRepositoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/git")
public class GitController {

    private final GitRepositoryService gitRepositoryService;

    @PostMapping("/info")
    public GitRepositoryInfoRs getRepositoryInfo(@RequestBody GitRepositoryInfoRq rq) {
        GitRepositoryInfo info = gitRepositoryService.getRepositoryInfo(rq.url(), rq.login(), rq.password());
        return new GitRepositoryInfoRs(info.branches(), info.folders());
    }
}
