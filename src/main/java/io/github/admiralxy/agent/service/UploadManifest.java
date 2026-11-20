package io.github.admiralxy.agent.service;

import java.util.List;

/**
 * Decrypted content of the "create upload" request.
 *
 * @param name  archive name without extension
 * @param files files in the order that defines their indexes
 */
public record UploadManifest(String name, List<UploadFileSpec> files) {
}
