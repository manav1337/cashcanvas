package com.cashcanvas.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.cashcanvas.common.logging.CorrelationIdFilter;
import com.cashcanvas.support.AbstractIntegrationTest;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registeredTokenAuthenticatesProtectedEndpoints() throws Exception {
        String token = registerUser();

        // Authenticated: passes security, then 404 because the upload doesn't exist.
        mockMvc.perform(get("/api/v1/statements/{id}", 999_999)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());

        // No token: stopped by security with a problem+json 401.
        mockMvc.perform(get("/api/v1/statements/{id}", 999_999))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = registerUser();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/v1/statements/{id}", 1)
                        .header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailReturns409IgnoringCase() throws Exception {
        String email = randomEmail();
        registerUser(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email.toUpperCase(), "password", TEST_PASSWORD,
                                "fullName", "Someone Else"))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginWorksWithCorrectPasswordAndFailsOtherwise() throws Exception {
        String email = randomEmail();
        registerUser(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", randomEmail(), "password", TEST_PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email", "password", "short", "fullName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("fullName")));
    }

    @Test
    void correlationIdIsEchoedOrGenerated() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(CorrelationIdFilter.HEADER, "test-correlation-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", randomEmail(), "password", TEST_PASSWORD))))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "test-correlation-0001"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-0001"));


        // Unsafe value (log-injection attempt) is replaced with a generated UUID.
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(CorrelationIdFilter.HEADER, "bad id\nINFO forged log line")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", randomEmail(), "password", TEST_PASSWORD))))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(header().string(CorrelationIdFilter.HEADER,
                        matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    private String json(Map<String, String> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
