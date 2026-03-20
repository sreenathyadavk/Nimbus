package com.localcloud.photos.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "devices")
public class Device {

    @Id
    private String id;

    private String deviceId;
    private Date firstSeen;
    private Date lastSeen;
    private boolean isBlocked;

    public Device() {
    }

    public Device(String deviceId, Date firstSeen, Date lastSeen, boolean isBlocked) {
        this.deviceId = deviceId;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.isBlocked = isBlocked;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public Date getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Date firstSeen) {
        this.firstSeen = firstSeen;
    }

    public Date getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Date lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }
}
