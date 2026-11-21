package com.cashcanvas.auth.service;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cashcanvas.auth.dto.AuthResponse;
import com.cashcanvas.auth.dto.LoginRequest;
import com.cashcanvas.auth.dto.RegisterRequest;
import com.cashcanvas.common.exception.ApiException;
import com.cashcanvas.common.exception.ErrorCode;
import com.cashcanvas.common.security.JwtService;
import com.cashcanvas.user.entity.User;
import com.cashcanvas.user.repository.UserRepository;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_CREDENTIALS_MESSAGE = "Email or password is incorrect";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    /**
     * Used when the email does not exist, so a failed login takes the same time
     * (one BCrypt check) whether or not the account exists.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equaliser-not-a-real-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }
        User user;
        try {
            user = userRepository.saveAndFlush(new User(
                    email,
                    passwordEncoder.encode(request.password()),
                    request.fullName().trim(),
                    clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent registrations with the same email: the UNIQUE constraint wins.
            throw emailTaken();
        }
        log.info("User registered userId={}", user.getId());
        return AuthResponse.bearer(jwtService.issue(user.getId(), user.getEmail()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normaliseEmail(request.email()));
        if (user.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
        }
        if (!passwordEncoder.matches(request.password(), user.get().getPasswordHash())) {
            log.info("Failed login userId={}", user.get().getId());
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
        }
        return AuthResponse.bearer(jwtService.issue(user.get().getId(), user.get().getEmail()));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException emailTaken() {
        return new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED, "An account with this email already exists");
    }
}
