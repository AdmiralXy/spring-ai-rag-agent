package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import io.github.admiralxy.agent.exception.FileTooLargeException;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.StoredFileNotFoundException;
import io.github.admiralxy.agent.repository.StoredFileRepository;
import io.github.admiralxy.agent.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 255;
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    private static final Pattern FORBIDDEN_NAME_CHARS = Pattern.compile("[/\\\\\\p{Cntrl}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final String DATA_URL_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";

    private final StoredFileRepository storedFileRepository;
    private final long maxSizeBytes;

    public FileStorageServiceImpl(
            StoredFileRepository storedFileRepository,
            @Value("${app.files.max-size:10MB}") DataSize maxSize
    ) {
        this.storedFileRepository = storedFileRepository;
        this.maxSizeBytes = maxSize.toBytes();
    }

    @Override
    @Transactional
    public StoredFile upload(String name, String extension, String base64Content) {
        String normalizedName = normalizeName(name);
        String normalizedExtension = normalizeExtension(extension);
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
        return new StoredFileContent(toDomain(entity), entity.getContent());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (storedFileRepository.deleteByIdDirectly(id) == 0) {
            throw new StoredFileNotFoundException(id);
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidFileException("Field 'name' is required");
        }
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidFileException("Field 'name' must be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (FORBIDDEN_NAME_CHARS.matcher(trimmed).find()) {
            throw new InvalidFileException("Field 'name' must not contain '/', '\\' or control characters");
        }
        return trimmed;
    }

    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new InvalidFileException("Field 'extension' is required");
        }
        String trimmed = extension.strip();
        if (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        if (!EXTENSION_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidFileException("Field 'extension' must be 1-20 latin letters or digits");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private byte[] decode(String base64Content) {
        if (base64Content == null || base64Content.isBlank()) {
            throw new InvalidFileException("Field 'content' is required");
        }
        String data = base64Content;
        if (data.startsWith(DATA_URL_PREFIX)) {
            int markerIndex = data.indexOf(BASE64_MARKER);
            if (markerIndex < 0) {
                throw new InvalidFileException("Field 'content' is a data URL without base64 payload");
            }
            data = data.substring(markerIndex + BASE64_MARKER.length());
        }
        data = WHITESPACE.matcher(data).replaceAll("");

        // Reject oversized payloads before allocating the decoded array.
        long estimatedSize = (long) data.length() * 3 / 4;
        if (estimatedSize > maxSizeBytes + 2) {
            throw new FileTooLargeException(maxSizeBytes);
        }

        byte[] content;
        try {
            content = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            throw new InvalidFileException("Field 'content' is not valid base64");
        }
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
