package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import io.github.admiralxy.agent.entity.StoredFileStorageRef;
import io.github.admiralxy.agent.exception.FileTooLargeException;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.StoredFileNotFoundException;
import io.github.admiralxy.agent.repository.StoredFileRepository;
import io.github.admiralxy.agent.service.FileStorageService;
import io.github.admiralxy.agent.service.file.Base64Content;
import io.github.admiralxy.agent.service.file.FileContentStore;
import io.github.admiralxy.agent.service.file.FileNameRules;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String ARCHIVE_EXTENSION = "tar.zst";
    private static final String ARCHIVE_CONTENT_TYPE = "application/zstd";

    private final StoredFileRepository storedFileRepository;
    private final FileContentStore contentStore;
    private final long maxSizeBytes;

    public FileStorageServiceImpl(
            StoredFileRepository storedFileRepository,
            FileContentStore contentStore,
            FilesProperties properties
    ) {
        this.storedFileRepository = storedFileRepository;
        this.contentStore = contentStore;
        this.maxSizeBytes = properties.getMaxSize().toBytes();
    }

    @Override
    @Transactional
    public StoredFile upload(String name, String extension, String base64Content) {
        String normalizedName = FileNameRules.normalizeName(name);
        String normalizedExtension = FileNameRules.normalizeExtension(extension);
        byte[] content = decode(base64Content);

        StoredFileEntity entity = new StoredFileEntity();
        entity.setName(normalizedName);
        entity.setExtension(normalizedExtension);
        entity.setContentType(resolveContentType(normalizedExtension));
        entity.setSize(content.length);
        entity.setContent(content);

        return toDomain(storedFileRepository.save(entity));
    }

    @Override
    @Transactional
    public StoredFile storeArchive(String name, Path archive) {
        String normalizedName = FileNameRules.normalizeName(name);
        try {
            long size = Files.size(archive);
            String key = contentStore.importFile(archive);
            onRollback(() -> contentStore.deleteQuietly(key));

            StoredFileEntity entity = new StoredFileEntity();
            entity.setName(normalizedName);
            entity.setExtension(ARCHIVE_EXTENSION);
            entity.setContentType(ARCHIVE_CONTENT_TYPE);
            entity.setSize(size);
            entity.setStoragePath(key);
            return toDomain(storedFileRepository.save(entity));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store archive " + archive, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StoredFile> getAll(int page, int size) {
        if (page < 0) {
            throw new InvalidFileException("Parameter 'page' must be >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidFileException("Parameter 'size' must be between 1 and " + MAX_PAGE_SIZE);
        }
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return storedFileRepository.findAllMetadata(PageRequest.of(page, size, sort));
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFileContent download(UUID id) {
        StoredFileEntity entity = storedFileRepository.findById(id)
                .orElseThrow(() -> new StoredFileNotFoundException(id));
        Resource body;
        if (entity.getStoragePath() != null) {
            Path path = contentStore.resolve(entity.getStoragePath());
            if (!Files.isReadable(path)) {
                throw new IllegalStateException("Content of file " + id + " is missing in storage");
            }
            body = new FileSystemResource(path);
        } else {
            body = new ByteArrayResource(entity.getContent());
        }
        return new StoredFileContent(toDomain(entity), body);
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
            afterCommit(() -> contentStore.deleteQuietly(ref.storagePath()));
        }
    }

    private byte[] decode(String base64Content) {
        String data = Base64Content.payload(base64Content);
        // Reject oversized payloads before allocating the decoded array.
        if (Base64Content.estimateDecodedSize(data) > maxSizeBytes + 2) {
            throw new FileTooLargeException(maxSizeBytes);
        }
        byte[] content = Base64Content.decode(data);
        if (content.length == 0) {
            throw new InvalidFileException("File must not be empty");
        }
        if (content.length > maxSizeBytes) {
            throw new FileTooLargeException(maxSizeBytes);
        }
        return content;
    }

    private String resolveContentType(String extension) {
        return MediaTypeFactory.getMediaType("file." + extension)
                .orElse(MediaType.APPLICATION_OCTET_STREAM)
                .toString();
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static void onRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    action.run();
                }
            }
        });
    }

    private StoredFile toDomain(StoredFileEntity entity) {
        return new StoredFile(
                entity.getId(),
                entity.getName(),
                entity.getExtension(),
                entity.getContentType(),
                entity.getSize(),
                entity.getCreatedAt()
        );
    }
}
