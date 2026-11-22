package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.service.file.UploadFileSpec;

import java.util.List;

public record UploadManifest(String name, List<UploadFileSpec> files) {
}
