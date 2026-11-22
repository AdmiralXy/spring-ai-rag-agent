package io.github.admiralxy.agent.domain;

import org.springframework.core.io.Resource;

public record StoredFileContent(StoredFile file, Resource content) {
}
