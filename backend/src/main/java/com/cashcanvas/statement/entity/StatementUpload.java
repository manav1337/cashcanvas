package com.cashcanvas.statement.entity;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.cashcanvas.statement.ingestion.IngestionResult;
import com.cashcanvas.statement.parser.RowError;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One uploaded file and its processing job. userId is a plain column (not a @ManyToOne)
 * because every query filters by it and the async worker must never lazy-load a User.
 * Status changes only through the mark* methods, which enforce the allowed transitions.
 */
@Entity
@Table(name = "statement_uploads")
public class StatementUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, updatable = false, length = 64)
    private String storedFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 8)
    private StatementFileType fileType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UploadStatus status;

    @Column(name = "bank_code", length = 16)
    private String bankCode;

    @Column(name = "account_last4", length = 4)
    private String accountLast4;

    @Column(name = "rows_total", nullable = false)
    private int rowsTotal;

    @Column(name = "rows_inserted", nullable = false)
    private int rowsInserted;

    @Column(name = "rows_duplicate", nullable = false)
    private int rowsDuplicate;

    @Column(name = "rows_failed", nullable = false)
    private int rowsFailed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_summary")
    private List<RowError> errorSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 64)
    private UploadFailureReason failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StatementUpload() {
        // for JPA
    }

    public static StatementUpload pending(Long userId, String originalFilename, String storedFilename,
                                          StatementFileType fileType, long fileSizeBytes, Instant now) {
        StatementUpload upload = new StatementUpload();
        upload.userId = userId;
        upload.originalFilename = originalFilename;
        upload.storedFilename = storedFilename;
        upload.fileType = fileType;
        upload.fileSizeBytes = fileSizeBytes;
        upload.status = UploadStatus.PENDING;
        upload.createdAt = now;
        upload.updatedAt = now;
        return upload;
    }

    public void markParsing(Instant now) {
        requireStatus(UploadStatus.PENDING);
        status = UploadStatus.PARSING;
        startedAt = now;
        updatedAt = now;
    }

    public void markCategorising(Instant now) {
        requireStatus(UploadStatus.PARSING);
        status = UploadStatus.CATEGORISING;
        updatedAt = now;
    }

    public void markCompleted(IngestionResult result, Instant now) {
        if (status != UploadStatus.PARSING && status != UploadStatus.CATEGORISING) {
            throw new IllegalStateException("Cannot complete upload " + id + " from status " + status);
        }
        bankCode = result.bankCode();
        accountLast4 = result.accountLast4();
        rowsTotal = result.rowsTotal();
        rowsInserted = result.rowsInserted();
        rowsDuplicate = result.rowsDuplicate();
        rowsFailed = result.rowsFailed();
        errorSummary = result.errors().isEmpty() ? null : result.errors();
        status = result.hasWarnings() ? UploadStatus.DONE_WITH_WARNINGS : UploadStatus.DONE;
        completedAt = now;
        updatedAt = now;
    }

    public void markFailed(UploadFailureReason reason, Instant now) {
        if (status.isTerminal()) {
            throw new IllegalStateException("Upload " + id + " is already " + status);
        }
        status = UploadStatus.FAILED;
        failureReason = reason;
        completedAt = now;
        updatedAt = now;
    }

    private void requireStatus(UploadStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Upload " + id + " is " + status + ", expected " + expected);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public StatementFileType getFileType() {
        return fileType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getAccountLast4() {
        return accountLast4;
    }

    public int getRowsTotal() {
        return rowsTotal;
    }

    public int getRowsInserted() {
        return rowsInserted;
    }

    public int getRowsDuplicate() {
        return rowsDuplicate;
    }

    public int getRowsFailed() {
        return rowsFailed;
    }

    public List<RowError> getErrorSummary() {
        return errorSummary == null ? List.of() : errorSummary;
    }

    public UploadFailureReason getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
