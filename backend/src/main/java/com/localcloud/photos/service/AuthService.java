package com.localcloud.photos.service;

import com.localcloud.photos.dto.DeviceRegistrationRequest;
import com.localcloud.photos.dto.LoginResponse;
import com.localcloud.photos.dto.RefreshRequest;
import com.localcloud.photos.model.AllowedDevice;
import com.localcloud.photos.model.RefreshToken;
import com.localcloud.photos.repository.AllowedDeviceRepository;
import com.localcloud.photos.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AllowedDeviceRepository allowedDeviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Value("${app.jwt.access-expiry-ms:900000}")
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-expiry-ms:2592000000}")
    private long refreshTokenExpiration;

    public AuthService(AllowedDeviceRepository allowedDeviceRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService) {
        this.allowedDeviceRepository = allowedDeviceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    public LoginResponse registerDevice(DeviceRegistrationRequest request) {
        // Auto-register: find existing or create new
        AllowedDevice device = allowedDeviceRepository.findByDeviceId(request.getDeviceId())
                .orElse(null);

        if (device == null) {
            // First time this device is seen — auto-register
            device = new AllowedDevice(request.getDeviceId(), request.getDeviceName());
            allowedDeviceRepository.save(device);
            log.info("New device auto-registered: {} ({})", request.getDeviceId(), request.getDeviceName());
        } else {
            // Existing device — update info
            device.setDeviceName(request.getDeviceName());
            device.setLastSeenAt(Instant.now());
            allowedDeviceRepository.save(device);
            log.info("Existing device re-authenticated: {} ({})", request.getDeviceId(), request.getDeviceName());
        }

        String accessToken = jwtService.generateAccessToken(request.getDeviceId());
        String refreshToken = createRefreshToken(request.getDeviceId());

        return new LoginResponse(accessToken, refreshToken, accessTokenExpiration / 1000);
    }

    public LoginResponse refresh(RefreshRequest request) {
        RefreshToken oldToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (oldToken.isRevoked()) {
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

        // Issue new pair — userId field stores deviceId
        String accessToken = jwtService.generateAccessToken(oldToken.getUserId());
        String newRefreshToken = createRefreshToken(oldToken.getUserId());

        return new LoginResponse(accessToken, newRefreshToken, accessTokenExpiration / 1000);
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private String createRefreshToken(String deviceId) {
        String token = jwtService.generateRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUserId(deviceId);
        refreshToken.setDeviceId(deviceId);
        refreshToken.setCreatedAt(new Date());
        refreshToken.setExpiresAt(new Date(System.currentTimeMillis() + refreshTokenExpiration));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);
        return token;
    }
}
