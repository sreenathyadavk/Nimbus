package com.localcloud.photos.controller;

import com.localcloud.photos.dto.MediaDTO;
import com.localcloud.photos.model.Media;
import com.localcloud.photos.service.MediaService;
import com.localcloud.photos.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;
    private final StorageService storageService;

    public MediaController(MediaService mediaService, StorageService storageService) {
        this.mediaService = mediaService;
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Media> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "hash", required = false) String hash,
            @RequestParam(value = "originalCreationDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date originalCreationDate,
            @RequestAttribute("deviceId") String deviceId) {

        Media media = mediaService.uploadMedia(file, hash, originalCreationDate, deviceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(media);
    }

    @GetMapping
    public ResponseEntity<Page<MediaDTO>> getAllMedia(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(mediaService.getAllActiveMediaPaginated(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Media> getMedia(@PathVariable String id) {
        return mediaService.getMediaById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMedia(@PathVariable String id) {
        mediaService.deleteMedia(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/hash/{hash}")
    public ResponseEntity<Map<String, Boolean>> checkHash(@PathVariable String hash) {
        boolean exists = mediaService.hashExists(hash);
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sync")
    public ResponseEntity<List<Media>> syncMedia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date since) {
        // For a full robust sync, client should just fetch active media, or we can
        // filter by 'since' upload Date
        // For simplicity, if 'since' is present we could filter, else return all.
        // As a baseline, return all active media entries and let the client reconcile.
        return ResponseEntity.ok(mediaService.getAllActiveMedia());
    }

    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String id) throws MalformedURLException {
        Optional<Media> mediaOptional = mediaService.getMediaById(id);
        if (mediaOptional.isPresent()) {
            Media media = mediaOptional.get();
            Path filePath = storageService.getRootLocation().resolve(media.getPath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "inline; filename=\"" + media.getOriginalFileName() + "\"")
                        .contentType(MediaType.parseMediaType(media.getMediaType()))
                        .body(resource);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/thumbnail/{id}")
    public ResponseEntity<Resource> downloadThumbnail(@PathVariable String id) throws MalformedURLException {
        Optional<Media> mediaOptional = mediaService.getMediaById(id);
        if (mediaOptional.isPresent() && mediaOptional.get().getThumbnailPath() != null) {
            Path filePath = storageService.getRootLocation().resolve(mediaOptional.get().getThumbnailPath())
                    .normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(resource);
            }
        }
        return ResponseEntity.notFound().build();
    }
}
