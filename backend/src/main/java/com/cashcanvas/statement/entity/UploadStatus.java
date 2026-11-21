package com.cashcanvas.statement.entity;

import java.util.EnumSet;
import java.util.Set;

/** PENDING -> PARSING -> CATEGORISING -> DONE | DONE_WITH_WARNINGS | FAILED */
public enum UploadStatus {
    PENDING,
    PARSING,
    CATEGORISING,
    DONE,
    DONE_WITH_WARNINGS,
    FAILED;

    /** Statuses in which a job is still queued or running. */
    public static final Set<UploadStatus> ACTIVE = EnumSet.of(PENDING, PARSING, CATEGORISING);

    public boolean isTerminal() {
        return !ACTIVE.contains(this);
    }
}
