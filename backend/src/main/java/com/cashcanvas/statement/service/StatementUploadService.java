package com.cashcanvas.statement.service;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cashcanvas.common.exception.ApiException;
import com.cashcanvas.common.exception.ErrorCode;
import com.cashcanvas.common.exception.ResourceNotFoundException;
import com.cashcanvas.statement.dto.UploadAcceptedResponse;
import com.cashcanvas.statement.dto.UploadStatusResponse;
import com.cashcanvas.statement.entity.StatementUpload;
import com.cashcanvas.statement.entity.UploadFailureReason;
import com.cashcanvas.statement.entity.UploadStatus;
import com.cashcanvas.statement.ingestion.IngestionWorker;
import com.cashcanvas.statement.repository.StatementUploadRepository;

/**
 * Upload flow ("direct submit"):
 *   validate -> save temp file -> save PENDING row (commits immediately) -> submit job to the pool
 *
 * upload() is deliberately NOT @Transactional. repository.save() runs in its own transaction and
 * has committed by the time we submit, so the worker thread is guaranteed to find the row.
 * (Submitting inside a still-open transaction is the classic bug: the worker starts, can't see
 * the uncommitted row, and the job silently does nothing.)
 *
 * If the pool rejects the job, we already have a committed row, so we mark it FAILED(QUEUE_FULL),
 * delete the temp file and return 429. Nothing is left stuck in PENDING.
 */
@Service
public class StatementUploadService {

    private static final Logger log = LoggerFactory.getLogger(StatementUploadService.class);

    private final UploadFileValidator validator;
    private final TempFileStore tempFileStore;
    private final StatementUploadRepository uploadRepository;
    private final IngestionWorker ingestionWorker;
    private final Clock clock;

    public StatementUploadService(UploadFileValidator validator, TempFileStore tempFileStore,
                                  StatementUploadRepository uploadRepository, IngestionWorker ingestionWorker,
                                  Clock clock) {
        this.validator = validator;
        this.tempFileStore = tempFileStore;
        this.uploadRepository = uploadRepository;
        this.ingestionWorker = ingestionWorker;
        this.clock = clock;
    }

    public UploadAcceptedResponse upload(Long userId, MultipartFile file) {
        ValidatedUpload validated = validator.validate(file);
        String storedFilename = tempFileStore.save(file, validated.fileType().extension());

        StatementUpload upload;
        try {
            upload = uploadRepository.save(StatementUpload.pending(
                    userId, validated.originalFilename(), storedFilename,
                    validated.fileType(), validated.sizeBytes(), clock.instant()));
        } catch (RuntimeException e) {
            tempFileStore.deleteQuietly(storedFilename);
            throw e;
        }

        try {
            ingestionWorker.process(upload.getId());
        } catch (TaskRejectedException e) {
            log.warn("Ingestion queue full; rejecting uploadId={}", upload.getId());
            upload.markFailed(UploadFailureReason.QUEUE_FULL, clock.instant());
            uploadRepository.save(upload);
            tempFileStore.deleteQuietly(storedFilename);
            throw new ApiException(ErrorCode.UPLOAD_QUEUE_FULL,
                    "Too many statements are being processed right now. Please retry in a minute.");
        }

        log.info("Upload accepted uploadId={} fileType={} sizeBytes={}",
                upload.getId(), validated.fileType(), validated.sizeBytes());
        return new UploadAcceptedResponse(upload.getId(), UploadStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public UploadStatusResponse getStatus(Long userId, Long uploadId) {
        return uploadRepository.findByIdAndUserId(uploadId, userId)
                .map(UploadStatusResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Statement upload", uploadId));
    }
}
