package com.cashcanvas.statement.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cashcanvas.statement.config.UploadProperties;

/**
 * Raw statements live here only until the worker has processed them, then they are deleted.
 * Files are named by a random UUID: the user's filename never reaches the filesystem.
 */
@Component
public class TempFileStore {

    private static final Logger log = LoggerFactory.getLogger(TempFileStore.class);

    private final Path baseDir;

    public TempFileStore(UploadProperties properties) throws IOException {
        this.baseDir = properties.tempDir().toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
        log.info("Upload temp directory: {}", baseDir);
    }

    /** @return the generated stored filename, e.g. 3f2b...e1.csv */
    public String save(MultipartFile file, String extension) {
        String storedName = UUID.randomUUID() + "." + extension;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, resolve(storedName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store uploaded file", e);
        }
        return storedName;
    }

    public Path resolve(String storedName) {
        Path path = baseDir.resolve(storedName).normalize();
        if (!path.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid stored file name");
        }
        return path;
    }

    public boolean exists(String storedName) {
        return Files.exists(resolve(storedName));
    }

    public void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not delete temp file {}", storedName, e);
        }
    }
}
