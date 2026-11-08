package com.theblastbeast.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blastbeast.storage")
public record StorageProperties(
        String endpoint,
        String bucket,
        String publicBaseUrl,
        String localRoot,
        long maxUploadBytes,
        int thumbWidth,
        int heroWidth) {}
