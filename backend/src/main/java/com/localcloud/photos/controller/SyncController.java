package com.localcloud.photos.controller;

import com.localcloud.photos.dto.SyncRequest;
import com.localcloud.photos.dto.SyncResponse;
import com.localcloud.photos.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/delta")
    public ResponseEntity<SyncResponse> getDelta(@RequestBody SyncRequest request) {
        SyncResponse response = new SyncResponse();
        response.setDelta(syncService.getDelta(request.getLastSyncedAt()));
        return ResponseEntity.ok(response);
    }
}
