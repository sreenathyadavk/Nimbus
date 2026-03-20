package com.localcloud.photos.dto;

public class DeviceRegistrationRequest {
    private String deviceId;
    private String deviceName;

    public DeviceRegistrationRequest() {}

    public DeviceRegistrationRequest(String deviceId, String deviceName) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
}
