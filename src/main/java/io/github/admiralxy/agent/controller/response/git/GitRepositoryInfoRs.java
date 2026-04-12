package io.github.admiralxy.agent.controller.response.git;

import java.util.List;

public record GitRepositoryInfoRs(List<String> branches, List<String> folders) {
}
