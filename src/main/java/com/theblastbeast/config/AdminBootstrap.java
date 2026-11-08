package com.theblastbeast.config;

import com.theblastbeast.domain.entity.Role;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.entity.UserStatus;
import com.theblastbeast.properties.BootstrapProperties;
import com.theblastbeast.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer usuario de redacción en entornos sin seed (producción).
 * Solo actúa cuando la tabla {@code users} está vacía y hay credenciales configuradas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrapProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapProperties.isEnabled()) {
            return;
        }
        if (userRepository.count() > 0) {
            log.debug("INIT - AdminBootstrap -> run() - ya existen usuarios, no se crea ninguno");
            return;
        }

        User admin = new User();
        admin.setEmail(bootstrapProperties.email().trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(bootstrapProperties.password()));
        admin.setDisplayName(bootstrapProperties.displayName() == null
                || bootstrapProperties.displayName().isBlank()
                        ? "Redacción Blast Beast"
                        : bootstrapProperties.displayName().trim());
        admin.setRole(Role.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);

        log.info("INIT - AdminBootstrap -> run() - usuario inicial creado: {}", admin.getEmail());
    }
}
