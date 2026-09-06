package com.univault.auth;

import com.univault.auth.dto.AuthResponse;
import com.univault.auth.dto.LoginRequest;
import com.univault.auth.dto.SignupRequest;
import com.univault.entity.AuthType;
import com.univault.entity.User;
import com.univault.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(409).build(); // email already registered
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // NOTE: AuthType constants (OAUTH2 / STATIC_KEY) were originally defined for
        // StorageProviderAccount, but User.authType reuses the same class to describe
        // how the *platform account itself* was created — STATIC_KEY here means
        // "authenticates with a stored password", as opposed to OAUTH2 (future:
        // "sign in with Google" for the platform login, not the Drive connection).
        // Flag this to the team if a dedicated enum is wanted instead.
        user.setAuthType(AuthType.STATIC_KEY);
        user.setEmailVerified(false);  // Email/password users start unverified

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // deliberately identical response whether the email or the password was
            // wrong — don't give an attacker a way to enumerate valid emails
            return ResponseEntity.status(401).build();
        }

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token));
    }
}