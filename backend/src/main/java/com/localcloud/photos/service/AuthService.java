package com.localcloud.photos.service;

import com.localcloud.photos.dto.LoginRequest;
import com.localcloud.photos.dto.LoginResponse;
import com.localcloud.photos.dto.RefreshRequest;
import com.localcloud.photos.model.RefreshToken;
import com.localcloud.photos.model.User;
import com.localcloud.photos.repository.RefreshTokenRepository;
import com.localcloud.photos.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.access-expiry-ms:900000}")
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-expiry-ms:2592000000}")
    private long refreshTokenExpiration;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request, String deviceId) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String accessToken = jwtService.generateAccessToken(user.getId());
        String refreshToken = createRefreshToken(user.getId(), deviceId);

        return new LoginResponse(accessToken, refreshToken, accessTokenExpiration / 1000);
    }

    public LoginResponse refresh(RefreshRequest request) {
        RefreshToken oldToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (oldToken.isRevoked()) {
            // Reuse detection: compromise! Revoke all tokens for this user
            refreshTokenRepository.deleteAllByUserId(oldToken.getUserId());
            throw new RuntimeException("Refresh token has been compromised and revoked");
        }

        if (oldToken.getExpiresAt().before(new Date())) {
            refreshTokenRepository.delete(oldToken);
            throw new RuntimeException("Refresh token expired");
        }

        // Revoke old token
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        // Issue new pair
        String accessToken = jwtService.generateAccessToken(oldToken.getUserId());
        String newRefreshToken = createRefreshToken(oldToken.getUserId(), oldToken.getDeviceId());

        return new LoginResponse(accessToken, newRefreshToken, accessTokenExpiration / 1000);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private String createRefreshToken(String userId, String deviceId) {
        String token = jwtService.generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUserId(userId);
        refreshToken.setDeviceId(deviceId);
        refreshToken.setCreatedAt(new Date());
        refreshToken.setExpiresAt(new Date(System.currentTimeMillis() + refreshTokenExpiration));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
        return token;
    }
}
