package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.domain.Space;
import io.github.admiralxy.agent.entity.SpaceEntity;
import io.github.admiralxy.agent.repository.SpaceRepository;
import io.github.admiralxy.agent.service.RagService;
import io.github.admiralxy.agent.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private static final String SPACE_NOT_FOUND = "Space not found";
    private static final String SORT_DIRECTION_COLUMN = "createdAt";

    private final SpaceRepository spaceRepository;
    private final RagService ragService;

    @Override
    public Page<Space> getAll(int size) {
        Pageable page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, SORT_DIRECTION_COLUMN));
        return spaceRepository.findAll(page)
                .map(s -> new Space(s.getId(), s.getName(), s.getCreatedAt()));
    }

    @Override
    public Space create(String name) {
        SpaceEntity space = new SpaceEntity();
        space.setName(name);

        return Optional.of(spaceRepository.save(space))
                .map(s -> new Space(s.getId(), s.getName(), s.getCreatedAt()))
                .orElseThrow();
    }

    @Override
    public void delete(UUID id) {
        if (!spaceRepository.existsById(id)) {
            throw new IllegalArgumentException(SPACE_NOT_FOUND);
        }
        spaceRepository.deleteById(id);
        ragService.deleteFromSpace(id.toString());
    }
}
