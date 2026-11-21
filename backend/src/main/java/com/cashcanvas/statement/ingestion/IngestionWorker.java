package com.cashcanvas.statement.ingestion;

import java.nio.file.Path;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.cashcanvas.statement.config.IngestionExecutorConfig;
import com.cashcanvas.statement.entity.StatementUpload;
import com.cashcanvas.statement.entity.UploadFailureReason;
import com.cashcanvas.statement.entity.UploadStatus;
import com.cashcanvas.statement.repository.StatementUploadRepository;
import com.cashcanvas.statement.service.TempFileStore;

/**
 * Processes one upload on the bounded ingestion pool.
 *
 * Calling {@link #process(long)} through the Spring proxy only *submits* the job. If the pool and
 * queue are full, the proxy throws TaskRejectedException synchronously to the caller, which is how
 * StatementUploadService returns 429.
 *
 * Each status change is saved in its own short transaction (repository.save), so the
 * GET /statements/{id} poller sees PARSING -> ... -> DONE as it happens.
 * The temp file is always deleted in finally, whatever the outcome.
 */
@Component
public class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);
    private static final String MDC_UPLOAD_ID = "uploadId";

    private final StatementUploadRepository uploadRepository;
    private final TempFileStore tempFileStore;
    private final Clock clock;

    public IngestionWorker(StatementUploadRepository uploadRepository, TempFileStore tempFileStore, Clock clock) {
        this.uploadRepository = uploadRepository;
        this.tempFileStore = tempFileStore;
        this.clock = clock;
    }

    @Async(IngestionExecutorConfig.INGESTION_EXECUTOR)
    public void process(long uploadId) {
        MDC.put(MDC_UPLOAD_ID, String.valueOf(uploadId));
        String storedFilename = null;
        try {
            StatementUpload upload = uploadRepository.findById(uploadId).orElse(null);
            if (upload == null) {
                log.warn("Upload not found, skipping");
                return;
            }
            storedFilename = upload.getStoredFilename();
            if (upload.getStatus() != UploadStatus.PENDING) {
                log.warn("Upload is {}, not PENDING; skipping", upload.getStatus());
                return;
            }

            upload.markParsing(clock.instant());
            upload = uploadRepository.save(upload);
            log.info("Ingestion started fileType={} sizeBytes={}", upload.getFileType(), upload.getFileSizeBytes());

            if (!tempFileStore.exists(storedFilename)) {
                upload.markFailed(UploadFailureReason.FILE_MISSING, clock.instant());
                uploadRepository.save(upload);
                log.error("Temp file missing, upload marked FAILED");
                return;
            }

            IngestionResult result = ingest(upload, tempFileStore.resolve(storedFilename));

            upload.markCompleted(result, clock.instant());
            uploadRepository.save(upload);
            log.info("Ingestion finished status={} total={} inserted={} duplicates={} failed={}",
                    upload.getStatus(), result.rowsTotal(), result.rowsInserted(),
                    result.rowsDuplicate(), result.rowsFailed());
        } catch (RuntimeException e) {
            log.error("Ingestion failed", e);
            markFailedSafely(uploadId);
        } finally {
            if (storedFilename != null) {
                tempFileStore.deleteQuietly(storedFilename);
            }
            MDC.remove(MDC_UPLOAD_ID);
        }
    }

    /**
     * Day 1 stub: the job flows end-to-end but reads nothing.
     * Day 2 replaces this with: detect parser -> parse rows -> hash -> batch insert -> CATEGORISING.
     */
    IngestionResult ingest(StatementUpload upload, Path file) {
        return IngestionResult.empty();
    }

    /** Re-reads the row (the in-memory copy may be stale after the failure) and marks it FAILED. */
    private void markFailedSafely(long uploadId) {
        try {
            uploadRepository.findById(uploadId)
                    .filter(u -> !u.getStatus().isTerminal())
                    .ifPresent(u -> {
                        u.markFailed(UploadFailureReason.PROCESSING_ERROR, clock.instant());
                        uploadRepository.save(u);
                    });
        } catch (RuntimeException e) {
            log.error("Could not mark upload as FAILED", e);
        }
    }
}
