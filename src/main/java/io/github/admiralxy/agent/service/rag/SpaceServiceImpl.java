package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.Space;
import io.github.admiralxy.agent.entity.SpaceEntity;
import io.github.admiralxy.agent.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private static final String SPACE_NOT_FOUND = "Space not found: %s";
    private static final String SORT_COLUMN = "createdAt";

    private final SpaceRepository spaceRepository;
    private final RagService ragService;

    @Override
    public Page<Space> getAll(int size) {
        return spaceRepository.findAll(PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, SORT_COLUMN)))
                .map(SpaceServiceImpl::toDomain);
    }

    @Override
    public Space create(String name) {
        SpaceEntity space = new SpaceEntity();
        space.setName(name);
        return toDomain(spaceRepository.save(space));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!spaceRepository.existsById(id)) {
            throw new IllegalArgumentException(SPACE_NOT_FOUND.formatted(id));
        }
        spaceRepository.deleteById(id);
        ragService.deleteSpace(id.toString());
    }

    private static Space toDomain(SpaceEntity space) {
        return new Space(space.getId(), space.getName(), space.getCreatedAt());
    }
}
