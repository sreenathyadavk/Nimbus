package com.localcloud.photos.service;

import com.localcloud.photos.dto.MediaDTO;
import com.localcloud.photos.model.Media;
import com.localcloud.photos.repository.MediaRepository;
import com.localcloud.photos.thumbnail.ThumbnailEngine;
import com.localcloud.photos.utils.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MediaService {

    private static final Logger logger = LoggerFactory.getLogger(MediaService.class);

    private final MediaRepository mediaRepository;
    private final StorageService storageService;
    private final ThumbnailEngine thumbnailEngine;

    public MediaService(MediaRepository mediaRepository, StorageService storageService,
            ThumbnailEngine thumbnailEngine) {
        this.mediaRepository = mediaRepository;
        this.storageService = storageService;
        this.thumbnailEngine = thumbnailEngine;
    }

    @Transactional
    public Media uploadMedia(MultipartFile file, String providedHash, Date originalCreationDate, String deviceId) {
        // Validate or compute hash
        String hash = providedHash;
        if (hash == null || hash.isEmpty()) {
            try {
                hash = HashUtils.calculateSHA256(file.getInputStream());
            } catch (Exception e) {
                throw new RuntimeException("Could not calculate hash from stream", e);
            }
        }

        // Check if exists
        Optional<Media> existing = mediaRepository.findByHash(hash);
        if (existing.isPresent()) {
            Media media = existing.get();
            if (media.isDeleted()) {
                // Recover soft deleted media
                media.setDeleted(false);
                media.setUploadDate(new Date());
                return mediaRepository.save(media);
            }
            logger.info("Media already exists with hash: {}", hash);
            return media; // Duplicate detected, return existing effectively making operation idempotent
        }

        Date creationDate = originalCreationDate != null ? originalCreationDate : new Date();

        // 1. Store File Physically
        String relativePath = storageService.storeFile(file, hash, creationDate);

        // 2. Draft Database Media Entry
        Media media = new Media();
        media.setHash(hash);
        media.setOriginalFileName(file.getOriginalFilename());
        media.setFileSize(file.getSize());
        media.setMediaType(file.getContentType());
        media.setUploadDate(new Date());
        media.setOriginalCreationDate(creationDate);
        media.setDeviceId(deviceId);
        media.setPath(relativePath);
        media.setDeleted(false);

        try {
            Media savedMedia = mediaRepository.save(media);

            // 3. Dispatch Async Thumbnail Generation
            thumbnailEngine.generateThumbnailAsync(relativePath, hash).thenAccept(thumbPath -> {
                if (thumbPath != null) {
                    savedMedia.setThumbnailPath(thumbPath);
                    mediaRepository.save(savedMedia);
                }
            });

            return savedMedia;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            logger.warn("DuplicateKeyException caught for hash: {}, returning existing media", hash);
            return mediaRepository.findByHash(hash).orElse(media);
        }
    }

    public List<Media> getAllActiveMedia() {
        return mediaRepository.findByIsDeletedFalse();
    }

    public Page<MediaDTO> getAllActiveMediaPaginated(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("uploadDate").descending());
        return mediaRepository.findByIsDeletedFalse(pageRequest).map(this::convertToDTO);
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

    public Optional<Media> getMediaById(String id) {
        return mediaRepository.findById(id);
    }

    public Optional<Media> getMediaByHash(String hash) {
        return mediaRepository.findByHash(hash);
    }

    @Transactional
    public void deleteMedia(String id) {
        mediaRepository.findById(id).ifPresent(media -> {
            media.setDeleted(true);
            mediaRepository.save(media);
        });
    }

    public boolean hashExists(String hash) {
        Optional<Media> existing = mediaRepository.findByHash(hash);
        if (existing.isPresent()) {
            return !existing.get().isDeleted();
        }
        return false;
    }
}
