package com.localcloud.photos.controller;

import com.localcloud.photos.dto.LoginRequest;
import com.localcloud.photos.dto.LoginResponse;
import com.localcloud.photos.dto.RefreshRequest;
import com.localcloud.photos.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId
    ) {
        return ResponseEntity.ok(authService.login(request, deviceId));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok().build();
    }
}
