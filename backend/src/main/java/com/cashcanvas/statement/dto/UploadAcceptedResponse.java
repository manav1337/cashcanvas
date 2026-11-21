package com.cashcanvas.statement.dto;

import com.cashcanvas.statement.entity.UploadStatus;

/** Body of 202 Accepted. Poll GET /api/v1/statements/{jobId} (also in the Location header). */
public record UploadAcceptedResponse(Long jobId, UploadStatus status) {
}
