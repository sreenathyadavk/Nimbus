package com.localcloud.photos.controller;

import com.localcloud.photos.dto.DeviceRegistrationRequest;
import com.localcloud.photos.dto.LoginResponse;
import com.localcloud.photos.dto.RefreshRequest;
import com.localcloud.photos.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-device")
    public ResponseEntity<LoginResponse> registerDevice(@RequestBody DeviceRegistrationRequest request) {
        LoginResponse response = authService.registerDevice(request);
        return ResponseEntity.ok(response);
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
