package com.cashcanvas.statement.controller;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.cashcanvas.common.security.AuthenticatedUser;
import com.cashcanvas.statement.dto.UploadAcceptedResponse;
import com.cashcanvas.statement.dto.UploadStatusResponse;
import com.cashcanvas.statement.service.StatementUploadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/statements")
@Tag(name = "Statements", description = "Upload bank statements and track processing")
public class StatementController {

    private final StatementUploadService uploadService;

    public StatementController(StatementUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a statement export (.csv, .xls, .xlsx; max 5 MB). Processing is asynchronous.")
    @ApiResponse(responseCode = "202", description = "Accepted; poll the Location URL for status")
    @ApiResponse(responseCode = "400", description = "FILE_EMPTY")
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE")
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_FILE_TYPE")
    @ApiResponse(responseCode = "429", description = "UPLOAD_QUEUE_FULL or RATE_LIMITED (see Retry-After)")
    public ResponseEntity<UploadAcceptedResponse> upload(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file) {
        UploadAcceptedResponse body = uploadService.upload(user.id(), file);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(body.jobId())
                .toUri();
        return ResponseEntity.accepted().location(location).body(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get processing status, row counts and row-level errors for an upload")
    @ApiResponse(responseCode = "200", description = "Current status")
    @ApiResponse(responseCode = "404", description = "RESOURCE_NOT_FOUND (also for other users' uploads)")
    public UploadStatusResponse status(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return uploadService.getStatus(user.id(), id);
    }
}
