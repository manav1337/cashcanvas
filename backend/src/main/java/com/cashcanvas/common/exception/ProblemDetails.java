package com.cashcanvas.common.exception;

import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

import com.cashcanvas.common.logging.CorrelationIdFilter;

/** Builds RFC 7807 bodies with the same extra fields everywhere: code, timestamp, correlationId. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        decorate(problem, code);
        return problem;
    }

    public static void decorate(ProblemDetail problem, ErrorCode code) {
        problem.setTitle(code.title());
        problem.setType(code.typeUri());
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now().toString());
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
