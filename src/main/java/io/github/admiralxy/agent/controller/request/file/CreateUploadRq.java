package io.github.admiralxy.agent.controller.request.file;

import java.util.List;

/**
 * Creates a chunked upload session.
 *
 * @param name  archive name without extension (the extension is always {@code zip})
 * @param files files to upload, in the order that defines their indexes
 */
public record CreateUploadRq(String name, List<CreateUploadFileRq> files) {
}
