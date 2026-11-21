package com.cashcanvas.statement.dto;

import java.time.Instant;
import java.util.List;

import com.cashcanvas.statement.entity.StatementFileType;
import com.cashcanvas.statement.entity.StatementUpload;
import com.cashcanvas.statement.entity.UploadFailureReason;
import com.cashcanvas.statement.entity.UploadStatus;
import com.cashcanvas.statement.parser.RowError;

public record UploadStatusResponse(
        Long jobId,
        UploadStatus status,
        String originalFilename,
        StatementFileType fileType,
        String bankCode,
        String accountLast4,
        RowCounts rows,
        UploadFailureReason failureReason,
        List<RowError> errors,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    public record RowCounts(int total, int inserted, int duplicate, int failed) {
    }

    public static UploadStatusResponse from(StatementUpload upload) {
        return new UploadStatusResponse(
                upload.getId(),
                upload.getStatus(),
                upload.getOriginalFilename(),
                upload.getFileType(),
                upload.getBankCode(),
                upload.getAccountLast4(),
                new RowCounts(upload.getRowsTotal(), upload.getRowsInserted(),
                        upload.getRowsDuplicate(), upload.getRowsFailed()),
                upload.getFailureReason(),
                upload.getErrorSummary(),
                upload.getCreatedAt(),
                upload.getStartedAt(),
                upload.getCompletedAt());
    }
}
