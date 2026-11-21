package com.cashcanvas.statement.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Bound from cashcanvas.upload.* in application.yml.
 *
 * @param tempDir                    where uploaded files wait for the worker (deleted after processing)
 * @param corePoolSize               worker threads always available
 * @param maxPoolSize                extra threads, created only once the queue is full
 * @param queueCapacity              jobs that may wait; beyond core+queue+extra threads -> 429
 * @param recoverStaleJobsOnStartup  mark jobs left PENDING/PARSING by a crash as FAILED on boot
 */
@ConfigurationProperties("cashcanvas.upload")
public record UploadProperties(
        Path tempDir,
        @DefaultValue("5MB") DataSize maxFileSize,
        @DefaultValue("2") int corePoolSize,
        @DefaultValue("4") int maxPoolSize,
        @DefaultValue("10") int queueCapacity,
        @DefaultValue("true") boolean recoverStaleJobsOnStartup) {
}
