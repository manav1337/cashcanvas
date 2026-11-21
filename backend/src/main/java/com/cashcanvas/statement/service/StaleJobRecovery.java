package com.cashcanvas.statement.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.cashcanvas.statement.config.UploadProperties;
import com.cashcanvas.statement.entity.StatementUpload;
import com.cashcanvas.statement.entity.UploadFailureReason;
import com.cashcanvas.statement.entity.UploadStatus;
import com.cashcanvas.statement.repository.StatementUploadRepository;

/**
 * The job queue lives in memory, so a restart loses queued and running jobs.
 * On startup, any upload still PENDING/PARSING/CATEGORISING was interrupted: mark it FAILED
 * and delete its temp file so the user sees a clear status and can re-upload.
 *
 * Correct only for a single app instance (our deployment). With several instances you would
 * need a per-instance owner column or a real queue.
 */
@Component
public class StaleJobRecovery {

    private static final Logger log = LoggerFactory.getLogger(StaleJobRecovery.class);

    private final StatementUploadRepository uploadRepository;
    private final TempFileStore tempFileStore;
    private final UploadProperties properties;
    private final Clock clock;

    public StaleJobRecovery(StatementUploadRepository uploadRepository, TempFileStore tempFileStore,
                            UploadProperties properties, Clock clock) {
        this.uploadRepository = uploadRepository;
        this.tempFileStore = tempFileStore;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedJobs() {
        if (!properties.recoverStaleJobsOnStartup()) {
            return;
        }
        List<StatementUpload> stale = uploadRepository.findByStatusIn(UploadStatus.ACTIVE);
        if (stale.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        for (StatementUpload upload : stale) {
            upload.markFailed(UploadFailureReason.INTERRUPTED_BY_RESTART, now);
            tempFileStore.deleteQuietly(upload.getStoredFilename());
        }
        uploadRepository.saveAll(stale);
        log.warn("Marked {} interrupted upload(s) as FAILED", stale.size());
    }
}
