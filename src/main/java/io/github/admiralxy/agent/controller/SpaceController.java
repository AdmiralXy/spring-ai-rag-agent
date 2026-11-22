package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.space.CreateSpaceRq;
import io.github.admiralxy.agent.controller.response.space.CreateSpaceRs;
import io.github.admiralxy.agent.controller.response.space.GetSpacesRs;
import io.github.admiralxy.agent.security.AuthUser;
import io.github.admiralxy.agent.service.rag.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/spaces")
public class SpaceController {

    private final SpaceService spaceService;

    @GetMapping
    public GetSpacesRs getAll(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "10") int size) {
        return new GetSpacesRs(spaceService.getAll(user.id(), size));
    }

    @PostMapping
    public CreateSpaceRs create(@AuthenticationPrincipal AuthUser user, @RequestBody CreateSpaceRq rq) {
        var space = spaceService.create(user.id(), rq.name());
        return new CreateSpaceRs(space.id(), space.name(), space.createdAt());
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        spaceService.delete(user.id(), id);
    }
}
