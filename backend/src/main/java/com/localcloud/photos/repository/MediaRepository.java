package com.localcloud.photos.repository;

import com.localcloud.photos.model.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends MongoRepository<Media, String> {

    // Global queries (kept for backwards compatibility)
    Optional<Media> findByHash(String hash);

    boolean existsByHash(String hash);

    List<Media> findByIsDeletedFalse();

    Page<Media> findByIsDeletedFalse(Pageable pageable);

    List<Media> findByUploadDateAfter(Date uploadDate);

    // Device-scoped queries
    Optional<Media> findByHashAndDeviceId(String hash, String deviceId);

    boolean existsByHashAndDeviceId(String hash, String deviceId);

    List<Media> findByDeviceIdAndIsDeletedFalse(String deviceId);

    Page<Media> findByDeviceIdAndIsDeletedFalse(String deviceId, Pageable pageable);

    Optional<Media> findByIdAndDeviceId(String id, String deviceId);

    List<Media> findByDeviceIdAndUploadDateAfter(String deviceId, Date uploadDate);
}
