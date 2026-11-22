package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.Space;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface SpaceService {

    Page<Space> getAll(UUID userId, int size);

    Space create(UUID userId, String name);

    void delete(UUID userId, UUID id);
}
