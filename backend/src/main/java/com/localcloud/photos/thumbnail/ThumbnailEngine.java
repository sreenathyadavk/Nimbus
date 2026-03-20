package com.localcloud.photos.thumbnail;

import com.localcloud.photos.config.AppProperties;
import com.localcloud.photos.service.StorageService;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Service
public class ThumbnailEngine {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailEngine.class);
    private final StorageService storageService;
    private final AppProperties appProperties;

    public ThumbnailEngine(StorageService storageService, AppProperties appProperties) {
        this.storageService = storageService;
        this.appProperties = appProperties;
    }

    @Async("thumbnailExecutor")
    public CompletableFuture<String> generateThumbnailAsync(String originalRelativePath, String hash) {
        try {
            Path originalFile = storageService.getRootLocation().resolve(originalRelativePath);
            Path thumbnailDir = storageService.getRootLocation().resolve("thumbnails");

            // Assume jpeg for thumbnail format
            String thumbnailFilename = hash + "_thumb.jpg";
            Path thumbnailFile = thumbnailDir.resolve(thumbnailFilename).normalize();

            // Check if file exists to prevent remaking for soft deletes reverting
            if (thumbnailFile.toFile().exists()) {
                return CompletableFuture
                        .completedFuture(storageService.getRootLocation().relativize(thumbnailFile).toString());
            }

            int size = appProperties.getThumbnail().getSize();
            double compression = appProperties.getThumbnail().getCompression();

            // Right now only images are natively thumbnailed by thumbnailator
            // Video thumbnails could be done via FFmpeg, but we will skip it for now or
            // return a generic video thumb
            String ext = storageService.getExtension(originalRelativePath).toLowerCase();
            if (ext.equals("mp4") || ext.equals("mov")) {
                // Mock video thumbnail logic or skip
                logger.info("Video thumbnailing skipping for now for: {}", originalRelativePath);
                return CompletableFuture.completedFuture(null);
            }

            Thumbnails.of(originalFile.toFile())
                    .size(size, size)
                    .outputQuality(compression)
                    .outputFormat("jpg")
                    .toFile(thumbnailFile.toFile());

            String relPath = storageService.getRootLocation().relativize(thumbnailFile).toString();
            return CompletableFuture.completedFuture(relPath);

        } catch (IOException e) {
            logger.error("Failed to generate thumbnail for {}: {}", originalRelativePath, e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }
}
