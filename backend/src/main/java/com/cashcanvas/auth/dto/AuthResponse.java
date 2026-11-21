package com.cashcanvas.auth.dto;

import com.cashcanvas.common.security.IssuedToken;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {

    public static AuthResponse bearer(IssuedToken token) {
        return new AuthResponse(token.token(), "Bearer", token.expiresInSeconds());
    }
}
