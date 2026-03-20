package com.localcloud.photos.dto;

import java.util.List;

public class SyncResponse {
    private List<MediaDTO> delta;

    public List<MediaDTO> getDelta() {
        return delta;
    }

    public void setDelta(List<MediaDTO> delta) {
        this.delta = delta;
    }
}
