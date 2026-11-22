package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import io.github.admiralxy.agent.entity.StoredFileStorageRef;
import io.github.admiralxy.agent.exception.FileTooLargeException;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.StoredFileNotFoundException;
import io.github.admiralxy.agent.repository.StoredFileRepository;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DECODED_SIZE_SLACK = 2;
    private static final String ARCHIVE_EXTENSION = "tar.zst";
    private static final String ARCHIVE_CONTENT_TYPE = "application/zstd";
    private static final String MEDIA_TYPE_PROBE = "file.%s";
    private static final String SORT_CREATED_AT = "createdAt";
    private static final String SORT_ID = "id";
    private static final String ARCHIVE_STORE_FAILED = "Failed to store archive %s";
    private static final String PAGE_INVALID = "Parameter 'page' must be >= 0";
    private static final String SIZE_INVALID = "Parameter 'size' must be between 1 and %d";
    private static final String CONTENT_MISSING = "Content of file %s is missing in storage";
    private static final String FILE_EMPTY = "File must not be empty";

    private final StoredFileRepository storedFileRepository;
    private final FileContentStore contentStore;
    private final long maxSizeBytes;

    public FileStorageServiceImpl(StoredFileRepository storedFileRepository,
                                  FileContentStore contentStore,
                                  FilesProperties properties) {
        this.storedFileRepository = storedFileRepository;
        this.contentStore = contentStore;
        this.maxSizeBytes = properties.getMaxSize().toBytes();
    }

    @Override
    @Transactional
    public StoredFile upload(String name, String extension, String base64Content) {
        String normalizedExtension = FileNameRules.normalizeExtension(extension);
        byte[] content = decode(base64Content);
        StoredFileEntity entity = newEntity(name, normalizedExtension, contentType(normalizedExtension),
                content.length);
        entity.setContent(content);
        return toDomain(storedFileRepository.save(entity));
    }

    @Override
    @Transactional
    public StoredFile storeArchive(String name, Path archive) {
        try {
            long size = Files.size(archive);
            String key = contentStore.importFile(archive);
            TransactionCallbacks.onRollback(() -> contentStore.deleteQuietly(key));
            StoredFileEntity entity = newEntity(name, ARCHIVE_EXTENSION, ARCHIVE_CONTENT_TYPE, size);
            entity.setStoragePath(key);
            return toDomain(storedFileRepository.save(entity));
        } catch (IOException e) {
            throw new UncheckedIOException(ARCHIVE_STORE_FAILED.formatted(archive), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StoredFile> getAll(int page, int size) {
        if (page < 0) {
            throw new InvalidFileException(PAGE_INVALID);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidFileException(SIZE_INVALID.formatted(MAX_PAGE_SIZE));
        }
        Sort sort = Sort.by(Sort.Order.desc(SORT_CREATED_AT), Sort.Order.desc(SORT_ID));
        return storedFileRepository.findAllMetadata(PageRequest.of(page, size, sort));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFileContent download(UUID id) {
        StoredFileEntity entity = storedFileRepository.findById(id)
                .orElseThrow(() -> new StoredFileNotFoundException(id));
        return new StoredFileContent(toDomain(entity), body(entity));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        StoredFileStorageRef ref = storedFileRepository.findStorageRef(id)
                .orElseThrow(() -> new StoredFileNotFoundException(id));
        if (storedFileRepository.deleteByIdDirectly(id) == 0) {
            throw new StoredFileNotFoundException(id);
        }
        if (ref.storagePath() != null) {
            TransactionCallbacks.afterCommit(() -> contentStore.deleteQuietly(ref.storagePath()));
        }
    }

    private Resource body(StoredFileEntity entity) {
        if (entity.getStoragePath() == null) {
            return new ByteArrayResource(entity.getContent());
        }
        Path path = contentStore.resolve(entity.getStoragePath());
        if (!Files.isReadable(path)) {
            throw new IllegalStateException(CONTENT_MISSING.formatted(entity.getId()));
        }
        return new FileSystemResource(path);
    }

    private byte[] decode(String base64Content) {
        String data = Base64Content.payload(base64Content);
        if (Base64Content.estimateDecodedSize(data) > maxSizeBytes + DECODED_SIZE_SLACK) {
            throw new FileTooLargeException(maxSizeBytes);
        }
        byte[] content = Base64Content.decode(data);
        if (content.length == 0) {
            throw new InvalidFileException(FILE_EMPTY);
        }
        if (content.length > maxSizeBytes) {
            throw new FileTooLargeException(maxSizeBytes);
        }
        return content;
    }

    private static StoredFileEntity newEntity(String name, String extension, String contentType, long size) {
        StoredFileEntity entity = new StoredFileEntity();
        entity.setName(FileNameRules.normalizeName(name));
        entity.setExtension(extension);
        entity.setContentType(contentType);
        entity.setSize(size);
        return entity;
    }

    private static String contentType(String extension) {
        return MediaTypeFactory.getMediaType(MEDIA_TYPE_PROBE.formatted(extension))
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();
    }

    private static StoredFile toDomain(StoredFileEntity entity) {
        return new StoredFile(entity.getId(), entity.getName(), entity.getExtension(), entity.getContentType(),
                entity.getSize(), entity.getCreatedAt());
    }
}
