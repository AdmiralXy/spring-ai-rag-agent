package io.github.admiralxy.agent.controller.request.file;

public record CreateUploadRq(Integer version, String keySalt, String iv, String data) {
}
