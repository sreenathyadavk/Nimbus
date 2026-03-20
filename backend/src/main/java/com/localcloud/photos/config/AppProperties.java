package com.localcloud.photos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "nimbus")
public class AppProperties {

    private Storage storage = new Storage();
    private Upload upload = new Upload();
    private Thumbnail thumbnail = new Thumbnail();

    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }

    public Upload getUpload() { return upload; }
    public void setUpload(Upload upload) { this.upload = upload; }

    public Thumbnail getThumbnail() { return thumbnail; }
    public void setThumbnail(Thumbnail thumbnail) { this.thumbnail = thumbnail; }

    public static class Storage {
        private String rootDirectory;

        public String getRootDirectory() { return rootDirectory; }
        public void setRootDirectory(String rootDirectory) { this.rootDirectory = rootDirectory; }
    }

    public static class Upload {
        private List<String> allowedTypes;
        private long maxSizeBytes;

        public List<String> getAllowedTypes() { return allowedTypes; }
        public void setAllowedTypes(List<String> allowedTypes) { this.allowedTypes = allowedTypes; }

        public long getMaxSizeBytes() { return maxSizeBytes; }
        public void setMaxSizeBytes(long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }
    }

    public static class Thumbnail {
        private int size;
        private double compression;

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }

        public double getCompression() { return compression; }
        public void setCompression(double compression) { this.compression = compression; }
    }
}
