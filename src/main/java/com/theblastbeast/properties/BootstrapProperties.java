package com.theblastbeast.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "blastbeast.bootstrap")
public record BootstrapProperties(String email, String password, String displayName) {

    public boolean isEnabled() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
