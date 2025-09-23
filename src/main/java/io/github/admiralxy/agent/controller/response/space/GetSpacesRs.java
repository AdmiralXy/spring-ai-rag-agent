package io.github.admiralxy.agent.controller.response.space;

import io.github.admiralxy.agent.domain.Space;
import org.springframework.data.domain.Page;

public record GetSpacesRs(Page<Space> spaces) {
}
