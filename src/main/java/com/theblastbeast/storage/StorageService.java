package com.theblastbeast.storage;

import org.springframework.core.io.Resource;

public interface StorageService {

    void store(String key, byte[] content);

    Resource load(String key);

    void delete(String key);

    boolean exists(String key);
}
