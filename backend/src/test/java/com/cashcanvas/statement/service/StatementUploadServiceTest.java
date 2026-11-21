package com.cashcanvas.statement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.cashcanvas.common.exception.ApiException;
import com.cashcanvas.common.exception.ErrorCode;
import com.cashcanvas.statement.dto.UploadAcceptedResponse;
import com.cashcanvas.statement.entity.StatementFileType;
import com.cashcanvas.statement.entity.StatementUpload;
import com.cashcanvas.statement.entity.UploadFailureReason;
import com.cashcanvas.statement.entity.UploadStatus;
import com.cashcanvas.statement.ingestion.IngestionWorker;
import com.cashcanvas.statement.repository.StatementUploadRepository;

/** Unit test (no Spring, no Docker) for the direct-submit flow and the 429 path. */
@ExtendWith(MockitoExtension.class)
class StatementUploadServiceTest {

    private static final long USER_ID = 7L;
    private static final long UPLOAD_ID = 42L;
    private static final String STORED_NAME = "0000-test.csv";

    @Mock
    private UploadFileValidator validator;
    @Mock
    private TempFileStore tempFileStore;
    @Mock
    private StatementUploadRepository uploadRepository;
    @Mock
    private IngestionWorker ingestionWorker;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC);
    private final MockMultipartFile file = new MockMultipartFile(
            "file", "statement.csv", "text/csv", "Date,Amount\n".getBytes(StandardCharsets.UTF_8));

    private StatementUploadService service;

    @BeforeEach
    void setUp() {
        service = new StatementUploadService(validator, tempFileStore, uploadRepository, ingestionWorker, clock);
        when(validator.validate(file)).thenReturn(
                new ValidatedUpload("statement.csv", StatementFileType.CSV, "text/csv", file.getSize()));
        when(tempFileStore.save(file, "csv")).thenReturn(STORED_NAME);
        when(uploadRepository.save(any(StatementUpload.class))).thenAnswer(invocation -> {
            StatementUpload upload = invocation.getArgument(0);
            if (upload.getId() == null) {
                ReflectionTestUtils.setField(upload, "id", UPLOAD_ID); // what the DB would do
            }
            return upload;
        });
    }

    @Test
    void savesRowBeforeSubmittingJob() {
        UploadAcceptedResponse response = service.upload(USER_ID, file);

        assertThat(response.jobId()).isEqualTo(UPLOAD_ID);
        assertThat(response.status()).isEqualTo(UploadStatus.PENDING);
        InOrder order = inOrder(uploadRepository, ingestionWorker);
        order.verify(uploadRepository).save(any(StatementUpload.class));
        order.verify(ingestionWorker).process(UPLOAD_ID);
        verify(tempFileStore, never()).deleteQuietly(any());
    }

    @Test
    void fullQueueMarksUploadFailedDeletesFileAndReturns429() {
        doThrow(new TaskRejectedException("queue full")).when(ingestionWorker).process(UPLOAD_ID);

        assertThatThrownBy(() -> service.upload(USER_ID, file))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_QUEUE_FULL);

        ArgumentCaptor<StatementUpload> saved = ArgumentCaptor.forClass(StatementUpload.class);
        verify(uploadRepository, times(2)).save(saved.capture());
        StatementUpload last = saved.getAllValues().getLast();
        assertThat(last.getStatus()).isEqualTo(UploadStatus.FAILED);
        assertThat(last.getFailureReason()).isEqualTo(UploadFailureReason.QUEUE_FULL);
        verify(tempFileStore).deleteQuietly(STORED_NAME);
    }
}
