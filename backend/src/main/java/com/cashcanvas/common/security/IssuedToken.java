package com.cashcanvas.common.security;

public record IssuedToken(String token, long expiresInSeconds) {
}
