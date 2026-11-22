package io.github.admiralxy.agent.service.rag;

import io.github.admiralxy.agent.domain.Space;
import io.github.admiralxy.agent.entity.SpaceEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private static final String SORT_COLUMN = "createdAt";
    private static final String NAME_REQUIRED = "The space name is required";
    private static final int MAX_PAGE_SIZE = 1000;

    private final SpaceRepository spaceRepository;

    @Override
    public Page<Space> getAll(UUID userId, int size) {
        PageRequest page = PageRequest.of(0, Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, SORT_COLUMN));
        return spaceRepository.findByUserId(userId, page).map(SpaceServiceImpl::toDomain);
    }

    @Override
    public Space create(UUID userId, String name) {
        if (StringUtils.isBlank(name)) {
            throw ApiException.badRequest(NAME_REQUIRED);
        }
        SpaceEntity space = new SpaceEntity();
        space.setUserId(userId);
        space.setName(name.trim());
        return toDomain(spaceRepository.save(space));
    }

    /**
     * Its chunks and chat links are removed by the database (ON DELETE CASCADE).
     */
    @Override
    @Transactional
    public void delete(UUID userId, UUID id) {
        SpaceEntity space = spaceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound(RagServiceImpl.SPACE_NOT_FOUND.formatted(id)));
        spaceRepository.delete(space);
    }

    private static Space toDomain(SpaceEntity space) {
        return new Space(space.getId(), space.getName(), space.getCreatedAt());
    }
}
