package com.localcloud.photos.repository;

import com.localcloud.photos.model.AllowedDevice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AllowedDeviceRepository extends MongoRepository<AllowedDevice, String> {
    Optional<AllowedDevice> findByDeviceId(String deviceId);
    boolean existsByDeviceId(String deviceId);
}
