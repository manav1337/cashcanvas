package com.cashcanvas.statement.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cashcanvas.common.exception.ApiException;
import com.cashcanvas.common.exception.ErrorCode;
import com.cashcanvas.statement.config.UploadProperties;
import com.cashcanvas.statement.entity.StatementFileType;

/**
 * Checks an upload before it touches disk:
 *  1. not empty and within the size limit
 *  2. extension is .csv / .xls / .xlsx
 *  3. Tika's detected type (from the file's magic bytes) matches that extension,
 *     so an .exe or .pdf renamed to statement.xlsx is rejected
 *  4. a CSV contains no NUL bytes (Tika treats any unrecognised bytes as text/plain)
 *
 * The client's Content-Type header is ignored: it is whatever the client says it is.
 */
@Component
public class UploadFileValidator {

    private static final Logger log = LoggerFactory.getLogger(UploadFileValidator.class);
    private static final int TEXT_SNIFF_BYTES = 8192;
    private static final int MAX_FILENAME_LENGTH = 255;

    /**
     * Tika's "x-tika-msoffice" / "x-tika-ooxml" are the container formats (OLE2 / zip) it reports
     * when it can't be more specific. Accepting them is safe: POI will reject a non-spreadsheet on Day 2.
     */
    private static final Map<StatementFileType, Set<String>> ALLOWED_TYPES = Map.of(
            StatementFileType.CSV, Set.of("text/csv", "text/plain"),
            StatementFileType.XLS, Set.of("application/vnd.ms-excel", "application/x-tika-msoffice"),
            StatementFileType.XLSX, Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/x-tika-ooxml"));

    private final Tika tika;
    private final long maxBytes;

    public UploadFileValidator(Tika tika, UploadProperties properties) {
        this.tika = tika;
        this.maxBytes = properties.maxFileSize().toBytes();
    }

    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.FILE_EMPTY, "The uploaded file is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, "Statements must be at most " + (maxBytes / 1024 / 1024) + " MB");
        }

        String filename = sanitiseFilename(file.getOriginalFilename());
        StatementFileType fileType = StatementFileType.fromFilename(filename)
                .orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "Only .csv, .xls and .xlsx statement exports are accepted"));

        String detectedType = detect(file, filename);
        if (!ALLOWED_TYPES.get(fileType).contains(detectedType)
                || (fileType == StatementFileType.CSV && !looksLikeText(file))) {
            log.info("Rejected upload: extension={} detectedType={}", fileType, detectedType);
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "File content does not look like a ." + fileType.extension() + " file");
        }
        return new ValidatedUpload(filename, fileType, detectedType, file.getSize());
    }

    private String detect(MultipartFile file, String filename) {
        try (InputStream in = file.getInputStream()) {
            return tika.detect(in, filename);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload for type detection", e);
        }
    }

    private static boolean looksLikeText(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(TEXT_SNIFF_BYTES);
            for (byte b : head) {
                if (b == 0) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }

    /**
     * Keeps only the last path segment and safe characters. The stored file on disk is named by UUID,
     * so this name is only for display, but it is still user input and must not carry "../" or control chars.
     */
    static String sanitiseFilename(String original) {
        if (original == null || original.isBlank()) {
            return "statement";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            return "statement";
        }
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(name.length() - MAX_FILENAME_LENGTH) : name;
    }
}
