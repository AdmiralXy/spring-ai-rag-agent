package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import org.springframework.data.domain.Page;

import java.nio.file.Path;
import java.util.UUID;

public interface FileStorageService {

    StoredFile upload(UUID userId, String name, String extension, String base64Content);

    StoredFile storeArchive(UUID userId, String name, Path archive);

    Page<StoredFile> getAll(UUID userId, int page, int size);

    StoredFileContent download(UUID userId, UUID id);

    void delete(UUID userId, UUID id);
}
