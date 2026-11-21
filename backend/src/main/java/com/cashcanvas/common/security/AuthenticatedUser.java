package com.cashcanvas.common.security;

/**
 * The principal stored in the SecurityContext. Controllers receive it with
 * {@code @AuthenticationPrincipal AuthenticatedUser user} and pass {@code user.id()} to services,
 * which use it in every query (ownership is enforced in SQL, not after loading).
 */
public record AuthenticatedUser(Long id, String email) {
}
