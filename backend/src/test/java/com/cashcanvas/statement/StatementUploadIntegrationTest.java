package com.cashcanvas.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.cashcanvas.statement.entity.StatementUpload;
import com.cashcanvas.statement.repository.StatementUploadRepository;
import com.cashcanvas.statement.service.TempFileStore;
import com.cashcanvas.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;

class StatementUploadIntegrationTest extends AbstractIntegrationTest {

    /** Synthetic data only: fake merchant, fake VPA, fake amounts. */
    private static final String SYNTHETIC_CSV = """
            Date,Narration,Debit,Credit,Balance
            01/11/2026,UPI/DR/000000000001/TESTCAFE/TEST/testcafe@okaxis,250.00,,9750.00
            02/11/2026,NEFT CR-TEST0000001-DEMO EMPLOYER PVT LTD,,50000.00,59750.00
            """;

    @Autowired
    private StatementUploadRepository uploadRepository;

    @Autowired
    private TempFileStore tempFileStore;

    @Test
    void uploadIsProcessedAsyncAndOnlyVisibleToItsOwner() throws Exception {
        String ownerToken = registerUser();
        String otherToken = registerUser();

        MvcResult accepted = mockMvc.perform(multipart("/api/v1/statements")
                        .file(csv("statement.csv", SYNTHETIC_CSV))
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isAccepted())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long jobId = ((Number) JsonPath.read(accepted.getResponse().getContentAsString(), "$.jobId")).longValue();

        // The worker runs on another thread: poll like the frontend will.
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/statements/{id}", jobId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("DONE")));

        // IDOR check: user B gets 404 (not 403) for user A's upload.
        mockMvc.perform(get("/api/v1/statements/{id}", jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // Raw statement must not linger on disk.
        StatementUpload upload = uploadRepository.findById(jobId).orElseThrow();
        assertThat(tempFileStore.exists(upload.getStoredFilename())).isFalse();
    }

    @Test
    void uploadWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/statements").file(csv("statement.csv", SYNTHETIC_CSV)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void executableRenamedToCsvIsRejected() throws Exception {
        byte[] fakeExe = new byte[512];
        fakeExe[0] = 'M';
        fakeExe[1] = 'Z';
        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", fakeExe);

        mockMvc.perform(multipart("/api/v1/statements").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerUser())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void unsupportedExtensionIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/statements").file(csv("statement.txt", SYNTHETIC_CSV))
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerUser())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/statements").file(csv("statement.csv", ""))
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_EMPTY"));
    }

    @Test
    void missingFilePartIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/statements")
                        .header(HttpHeaders.AUTHORIZATION, bearer(registerUser())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    private static MockMultipartFile csv(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
}
