package com.localcloud.photos.service;

import com.localcloud.photos.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
public class StorageService {

    private final Path rootLocation;
    private final AppProperties appProperties;

    public StorageService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.rootLocation = Paths.get("/app/storage").toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.rootLocation);
            Files.createDirectories(this.rootLocation.resolve("images"));
            Files.createDirectories(this.rootLocation.resolve("videos"));
            Files.createDirectories(this.rootLocation.resolve("thumbnails"));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage directory", e);
        }
    }

    public void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Failed to store empty file.");
        }

        String contentType = file.getContentType();
        List<String> allowedTypes = appProperties.getUpload().getAllowedTypes();
        if (contentType == null || !allowedTypes.contains(contentType)) {
            throw new IllegalArgumentException("File type not allowed: " + contentType);
        }

        if (file.getSize() > appProperties.getUpload().getMaxSizeBytes()) {
            throw new IllegalArgumentException("File exceeds maximum allowed size.");
        }
    }

    public String storeFile(MultipartFile file, String hash, Date creationDate) {
        validateFile(file);

        try {
            String originalFilename = StringUtils
                    .cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
            String extension = getExtension(originalFilename);
            if (extension.isEmpty()) {
                // Infer from content type
                extension = getExtensionFromContextType(file.getContentType());
            }

            String filename = hash + "." + extension;
            Path destinationDir = getDirectoryForDate(creationDate,
                    file.getContentType() != null && file.getContentType().startsWith("video"));
            Path destinationFile = destinationDir.resolve(filename).normalize();

            if (!destinationFile.getParent().startsWith(this.rootLocation.normalize())) {
                throw new SecurityException("Cannot store file outside current directory.");
            }

            if (Files.exists(destinationFile)) {
                return this.rootLocation.relativize(destinationFile).toString().replace("\\", "/");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return this.rootLocation.relativize(destinationFile).toString().replace("\\", "/");

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + hash, e);
        }
    }

    public void deleteFile(String relativePath) {
        try {
            Path file = rootLocation.resolve(relativePath).normalize();
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + relativePath, e);
        }
    }

    private Path getDirectoryForDate(Date creationDate, boolean isVideo) throws IOException {
        LocalDate localDate = creationDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String year = String.valueOf(localDate.getYear());
        String month = String.format("%02d", localDate.getMonthValue());

        Path baseDir = rootLocation.resolve(isVideo ? "videos" : "images");
        Path finalDir = baseDir.resolve(year).resolve(month);

        Files.createDirectories(finalDir);
        return finalDir;
    }

    public String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    private String getExtensionFromContextType(String contentType) {
        if (contentType == null)
            return "bin";
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "video/mp4" -> "mp4";
            case "video/quicktime" -> "mov";
            default -> "bin";
        };
    }

    public Path getRootLocation() {
        return rootLocation;
    }
}
