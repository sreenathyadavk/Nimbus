package com.localcloud.photos.repository;

import com.localcloud.photos.model.Media;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends MongoRepository<Media, String> {
    Optional<Media> findByHash(String hash);

    boolean existsByHash(String hash);

    List<Media> findByIsDeletedFalse();

    Page<Media> findByIsDeletedFalse(Pageable pageable);

    List<Media> findByUploadDateAfter(java.util.Date uploadDate);
}
