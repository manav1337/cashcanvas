package com.cashcanvas.statement.ingestion;

import java.util.List;

import com.cashcanvas.statement.parser.RowError;

/** What the worker learned from one file. Built by the parser + writer from Day 2. */
public record IngestionResult(
        String bankCode,
        String accountLast4,
        int rowsTotal,
        int rowsInserted,
        int rowsDuplicate,
        int rowsFailed,
        List<RowError> errors) {

    public IngestionResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static IngestionResult empty() {
        return new IngestionResult(null, null, 0, 0, 0, 0, List.of());
    }

    public boolean hasWarnings() {
        return rowsFailed > 0 || !errors.isEmpty();
    }
}
