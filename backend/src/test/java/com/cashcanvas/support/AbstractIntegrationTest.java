package com.cashcanvas.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Base class for tests that need the whole app + real MySQL/Redis.
 * Keep the annotations identical in every subclass's hierarchy so Spring reuses ONE context
 * (and one pair of containers) for the entire suite. Don't add @MockitoBean in subclasses
 * unless you accept a second context.
 * Each test registers fresh users with random emails, so tests don't depend on each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    protected static final String TEST_PASSWORD = "Str0ngPass!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    /** Registers a new user and returns their access token. */
    protected String registerUser() throws Exception {
        return registerUser(randomEmail());
    }

    protected String registerUser(String email) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", TEST_PASSWORD,
                "fullName", "Test User"));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
