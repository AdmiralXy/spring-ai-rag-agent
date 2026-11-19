package io.github.admiralxy.agent.controller.response.file;

import org.springframework.data.domain.Page;

public record GetFilesRs(Page<FileRs> files) {
}
