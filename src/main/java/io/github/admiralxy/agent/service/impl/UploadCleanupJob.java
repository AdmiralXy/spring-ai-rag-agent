package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.service.UploadSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically removes abandoned upload sessions together with their chunks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadCleanupJob {

    private final UploadSessionService uploadSessionService;

    @Scheduled(
            initialDelayString = "${app.files.uploads.cleanup-interval:PT15M}",
            fixedDelayString = "${app.files.uploads.cleanup-interval:PT15M}"
    )
    public void removeExpiredSessions() {
        try {
            int removed = uploadSessionService.cleanupExpired();
            if (removed > 0) {
                log.info("Removed {} expired upload session(s)", removed);
            }
        } catch (RuntimeException e) {
            log.warn("Upload sessions cleanup failed", e);
        }
    }
}
