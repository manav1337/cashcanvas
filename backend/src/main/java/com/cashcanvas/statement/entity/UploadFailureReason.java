package com.cashcanvas.statement.entity;

/** File-level failure reasons. Row-level problems go into error_summary instead. */
public enum UploadFailureReason {
    /** The async pool and its queue were full when the upload arrived (API returned 429). */
    QUEUE_FULL,
    /** The app restarted while this job was queued or running. */
    INTERRUPTED_BY_RESTART,
    /** The temp file disappeared before the worker picked the job up. */
    FILE_MISSING,
    /** No parser recognised the header row (used from Day 2). */
    UNSUPPORTED_FORMAT,
    /** Any unexpected exception in the worker. Details are in the logs, not the API. */
    PROCESSING_ERROR
}
