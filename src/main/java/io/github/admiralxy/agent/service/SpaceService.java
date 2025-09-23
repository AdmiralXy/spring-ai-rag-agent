package io.github.admiralxy.agent.service;

import io.github.admiralxy.agent.domain.Space;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface SpaceService {

    /**
     * Get all available spaces.
     *
     * @param size page size
     * @return list of space names
     */
    Page<Space> getAll(int size);

    /**
     * Create space.
     *
     * @param name space name
     * @return created space
     */
    Space create(String name);

    /**
     * Delete a specific space by its name.
     *
     * @param id space id
     */
    void delete(UUID id);
}
