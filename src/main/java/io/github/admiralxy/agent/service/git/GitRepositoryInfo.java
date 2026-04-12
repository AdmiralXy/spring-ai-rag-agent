package io.github.admiralxy.agent.service.git;

import java.util.List;

public record GitRepositoryInfo(List<String> branches, List<String> folders) {
}
