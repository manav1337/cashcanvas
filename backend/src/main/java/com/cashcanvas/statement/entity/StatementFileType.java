package com.cashcanvas.statement.entity;

import java.util.Locale;
import java.util.Optional;

public enum StatementFileType {
    CSV,
    XLS,
    XLSX;

    public String extension() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<StatementFileType> fromFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dot + 1).toUpperCase(Locale.ROOT);
        for (StatementFileType type : values()) {
            if (type.name().equals(extension)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
