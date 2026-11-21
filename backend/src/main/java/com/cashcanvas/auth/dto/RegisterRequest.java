package com.cashcanvas.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password max is 72 because BCrypt ignores everything after 72 bytes. */
public record RegisterRequest(
        @Schema(example = "asha.demo@example.com")
        @NotBlank @Email @Size(max = 254) String email,

        @Schema(example = "Str0ngPass!")
        @NotBlank @Size(min = 8, max = 72) String password,

        @Schema(example = "Asha Demo")
        @NotBlank @Size(max = 100) String fullName) {
}
