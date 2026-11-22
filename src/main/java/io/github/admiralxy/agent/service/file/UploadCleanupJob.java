package io.github.admiralxy.agent.service.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UploadCleanupJob {

    private static final String INTERVAL = "${app.files.uploads.cleanup-interval:PT15M}";
    private static final String REMOVED = "Removed {} expired upload session(s)";
    private static final String FAILED = "Upload sessions cleanup failed";

    private final UploadSessionService uploadSessionService;

    @Scheduled(initialDelayString = INTERVAL, fixedDelayString = INTERVAL)
    public void removeExpiredSessions() {
        try {
            int removed = uploadSessionService.cleanupExpired();
            if (removed > 0) {
                log.info(REMOVED, removed);
            }
        } catch (RuntimeException e) {
            log.warn(FAILED, e);
        }
    }
}
