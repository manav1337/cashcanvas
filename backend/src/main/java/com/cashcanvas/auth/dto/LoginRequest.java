package com.cashcanvas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "asha.demo@example.com")
        @NotBlank @Size(max = 254) String email,

        @Schema(example = "Str0ngPass!")
        @NotBlank @Size(max = 72) String password) {
}
