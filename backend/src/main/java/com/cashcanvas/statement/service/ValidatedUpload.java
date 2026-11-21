package com.cashcanvas.statement.service;

import com.cashcanvas.statement.entity.StatementFileType;

public record ValidatedUpload(
        String originalFilename,
        StatementFileType fileType,
        String detectedContentType,
        long sizeBytes) {
}
