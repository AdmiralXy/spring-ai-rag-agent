package io.github.admiralxy.agent.domain;

import org.springframework.core.io.Resource;

/**
 * File metadata with a streamable body.
 */
public record StoredFileContent(StoredFile file, Resource content) {
}
