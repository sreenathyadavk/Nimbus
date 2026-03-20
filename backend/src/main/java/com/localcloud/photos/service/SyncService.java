package com.localcloud.photos.service;

import com.localcloud.photos.dto.MediaDTO;
import com.localcloud.photos.model.Media;
import com.localcloud.photos.repository.MediaRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private final MediaRepository mediaRepository;

    public SyncService(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    public List<MediaDTO> getDelta(String deviceId, Date lastSyncedAt) {
        Date sinceTime = lastSyncedAt != null ? lastSyncedAt : new Date(0);
        List<Media> deltaMedia = mediaRepository.findByDeviceIdAndUploadDateAfter(deviceId, sinceTime);
        return deltaMedia.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private MediaDTO convertToDTO(Media media) {
        MediaDTO dto = new MediaDTO();
        dto.setId(media.getId());
        dto.setOriginalFileName(media.getOriginalFileName());
        dto.setFileSize(media.getFileSize());
        dto.setMediaType(media.getMediaType());
        dto.setUploadDate(media.getUploadDate());
        dto.setOriginalCreationDate(media.getOriginalCreationDate());
        dto.setDeviceId(media.getDeviceId());
        dto.setWidth(media.getWidth());
        dto.setHeight(media.getHeight());
        dto.setDuration(media.getDuration());
        return dto;
    }
}
