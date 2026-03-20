package com.localcloud.photos.dto;

import java.util.Date;

public class MediaDTO {
    private String id;
    private String originalFileName;
    private long fileSize;
    private String mediaType;
    private Date uploadDate;
    private Date originalCreationDate;
    private String deviceId;
    private Integer width;
    private Integer height;
    private Long duration;

    public MediaDTO() {
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public Date getUploadDate() { return uploadDate; }
    public void setUploadDate(Date uploadDate) { this.uploadDate = uploadDate; }

    public Date getOriginalCreationDate() { return originalCreationDate; }
    public void setOriginalCreationDate(Date originalCreationDate) { this.originalCreationDate = originalCreationDate; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }
}
