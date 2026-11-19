package io.github.admiralxy.agent.service;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import org.springframework.data.domain.Page;

import java.nio.file.Path;
import java.util.UUID;

public interface FileStorageService {

    /**
     * Decodes base64 content and stores it as a file.
     *
     * @param name          file name without extension
     * @param extension     file extension, with or without a leading dot
     * @param base64Content plain base64 string or a data URL ({@code data:<mime>;base64,<data>})
     * @return stored file metadata
     */
    StoredFile upload(String name, String extension, String base64Content);

    /**
     * Moves a ready ZIP archive into disk storage and registers it as a file ({@code <name>.zip}).
     * Not subject to the POST /files size limit. If the surrounding transaction rolls back,
     * the moved archive is deleted.
     */
    StoredFile storeArchive(String name, Path archive);

    /**
     * Returns file metadata sorted from newest to oldest.
     */
    Page<StoredFile> getAll(int page, int size);

    StoredFileContent download(UUID id);

    void delete(UUID id);
}
