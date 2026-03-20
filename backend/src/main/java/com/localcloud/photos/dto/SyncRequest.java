package com.localcloud.photos.dto;

import java.util.Date;

public class SyncRequest {
    private Date lastSyncedAt;

    public Date getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Date lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
