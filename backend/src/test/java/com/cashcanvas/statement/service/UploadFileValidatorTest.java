package com.cashcanvas.statement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import com.cashcanvas.common.exception.ApiException;
import com.cashcanvas.common.exception.ErrorCode;
import com.cashcanvas.statement.config.UploadProperties;
import com.cashcanvas.statement.entity.StatementFileType;

/** Pure unit test: real Tika, real POI-generated spreadsheets, no Spring or Docker. */
class UploadFileValidatorTest {

    private final UploadFileValidator validator = validatorWithLimit(DataSize.ofMegabytes(5));

    @Test
    void acceptsCsv() {
        ValidatedUpload result = validator.validate(file("statement.csv", "Date,Narration,Amount\n01/11/2026,TEST,1.00\n"
                .getBytes(StandardCharsets.UTF_8)));
        assertThat(result.fileType()).isEqualTo(StatementFileType.CSV);
    }

    @Test
    void acceptsRealXlsx() throws IOException {
        ValidatedUpload result = validator.validate(file("statement.xlsx", workbookBytes(new XSSFWorkbook())));
        assertThat(result.fileType()).isEqualTo(StatementFileType.XLSX);
    }

    @Test
    void acceptsRealXls() throws IOException {
        ValidatedUpload result = validator.validate(file("statement.xls", workbookBytes(new HSSFWorkbook())));
        assertThat(result.fileType()).isEqualTo(StatementFileType.XLS);
    }

    @Test
    void rejectsXlsxRenamedToXls() throws IOException {
        assertRejected(file("statement.xls", workbookBytes(new XSSFWorkbook())), ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void rejectsPdfRenamedToXlsx() {
        byte[] pdf = "%PDF-1.7\n1 0 obj\n<< >>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
        assertRejected(file("statement.xlsx", pdf), ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void rejectsBinaryRenamedToCsv() {
        byte[] binary = new byte[256];
        binary[0] = 'M';
        binary[1] = 'Z';
        assertRejected(file("statement.csv", binary), ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertRejected(file("statement.pdf", "hello".getBytes(StandardCharsets.UTF_8)), ErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    void rejectsEmptyFile() {
        assertRejected(file("statement.csv", new byte[0]), ErrorCode.FILE_EMPTY);
    }

    @Test
    void rejectsOversizedFile() {
        UploadFileValidator tiny = validatorWithLimit(DataSize.ofBytes(10));
        assertThatThrownBy(() -> tiny.validate(file("statement.csv", "0123456789ABCDEF".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.FILE_TOO_LARGE);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "../../etc/statement.csv     | statement.csv",
            "C:\\Users\\me\\Nov 2026.xls | Nov 2026.xls",
            "stmt<script>.csv            | stmt_script_.csv",
            "...                         | statement",
    })
    void sanitisesFilenames(String original, String expected) {
        assertThat(UploadFileValidator.sanitiseFilename(original)).isEqualTo(expected);
    }

    private void assertRejected(MockMultipartFile file, ErrorCode expected) {
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private static UploadFileValidator validatorWithLimit(DataSize limit) {
        return new UploadFileValidator(new Tika(),
                new UploadProperties(Path.of("unused"), limit, 2, 4, 10, false));
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static byte[] workbookBytes(Workbook workbook) throws IOException {
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Statement").createRow(0).createCell(0).setCellValue("Date");
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
