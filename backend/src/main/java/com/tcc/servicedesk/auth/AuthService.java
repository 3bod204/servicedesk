package com.tcc.servicedesk.auth;

import com.tcc.servicedesk.auth.dto.LoginRequest;
import com.tcc.servicedesk.auth.dto.LoginResponse;
import com.tcc.servicedesk.auth.dto.RefreshRequest;
import com.tcc.servicedesk.security.JwtService;
import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptRepository loginAttemptRepository;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            LoginAttemptRepository loginAttemptRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptRepository = loginAttemptRepository;
    }

    public LoginResponse login(LoginRequest request) {

        checkRateLimit(request.email());

        Authentication authentication;
        try {
            authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.email(), request.password()));
        } catch (AuthenticationException ex) {
            recordAttempt(request.email(), false);
            throw ex;
        }

        recordAttempt(request.email(), true);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        User user =
                userRepository
                        .findById(principal.getId())
                        .orElseThrow(() -> new BadCredentialsException("User not found"));

        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String rawRefreshToken = jwtService.generateRefreshToken(user.getEmail());

        RefreshToken refreshTokenEntity =
                RefreshToken.builder()
                        .user(user)
                        .tokenHash(passwordEncoder.encode(rawRefreshToken))
                        .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                        .createdAt(Instant.now())
                        .build();

        refreshTokenRepository.save(refreshTokenEntity);

        return new LoginResponse(
                accessToken, rawRefreshToken, user.getId(), user.getFullName(), user.getEmail());
    }

    private void checkRateLimit(String email) {
        Instant since = Instant.now().minus(15, ChronoUnit.MINUTES);
        long failedAttempts =
                loginAttemptRepository.countByEmailIgnoreCaseAndSuccessfulFalseAndAttemptedAtAfter(
                        email, since);

        if (failedAttempts >= 5) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed login attempts. Try again in 15 minutes.");
        }
    }

    private void recordAttempt(String email, boolean successful) {
        LoginAttempt attempt =
                LoginAttempt.builder()
                        .email(email)
                        .successful(successful)
                        .attemptedAt(Instant.now())
                        .build();
        loginAttemptRepository.save(attempt);
    }

    public LoginResponse refresh(RefreshRequest request) {

        String submittedToken = request.refreshToken();

        String email = jwtService.extractEmail(submittedToken);

        if (jwtService.isTokenExpired(submittedToken)) {
            throw new BadCredentialsException("Refresh token expired");
        }

        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(() -> new BadCredentialsException("User not found"));

        RefreshToken storedToken =
                refreshTokenRepository.findAllByUserAndRevokedFalse(user).stream()
                        .filter(rt -> passwordEncoder.matches(submittedToken, rt.getTokenHash()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BadCredentialsException(
                                                "Refresh token not recognized or revoked"));

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token expired");
        }

        String newAccessToken = jwtService.generateAccessToken(user.getEmail());

        return new LoginResponse(
                newAccessToken, submittedToken, user.getId(), user.getFullName(), user.getEmail());
    }

    public void logout(RefreshRequest request) {
        String submittedToken = request.refreshToken();
        String email = jwtService.extractEmail(submittedToken);

        User user =
                userRepository
                        .findByEmailIgnoreCase(email)
                        .orElseThrow(() -> new BadCredentialsException("User not found"));

        RefreshToken storedToken =
                refreshTokenRepository.findAllByUserAndRevokedFalse(user).stream()
                        .filter(rt -> passwordEncoder.matches(submittedToken, rt.getTokenHash()))
                        .findFirst()
                        .orElseThrow(
                                () -> new BadCredentialsException("Refresh token not recognized"));

        storedToken.revoke();
        refreshTokenRepository.save(storedToken);
    }
}
