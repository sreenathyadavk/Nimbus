package com.localcloud.photos.repository;

import com.localcloud.photos.model.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findAllByUserId(String userId);
    void deleteAllByUserId(String userId);
}
