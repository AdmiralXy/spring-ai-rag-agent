package io.github.admiralxy.agent.service.git;

import java.util.List;

public interface GitRepositoryService {

    GitRepositoryInfo getRepositoryInfo(String repositoryUrl, String login, String password);

    List<String> getFileChunks(String repositoryUrl, String branch, String folder, String login, String password);
}
