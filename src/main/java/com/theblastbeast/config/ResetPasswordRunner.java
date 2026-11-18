package com.theblastbeast.config;

import com.theblastbeast.properties.ResetPasswordProperties;
import com.theblastbeast.service.PasswordResetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Tarea de mantenimiento para restablecer la contraseña de un administrador sin
 * depender del correo. Se activa con el perfil {@code reset-password}:
 *
 * <pre>
 * SPRING_PROFILES_ACTIVE=prod,reset-password \
 * RESET_PASSWORD_EMAIL=redaccion@theblastbeast.com \
 * RESET_PASSWORD='...' java -jar app.jar
 * </pre>
 *
 * <p>El proceso no abre el puerto HTTP (ver {@code application-reset-password.yml})
 * y termina con código 0 si el cambio fue correcto.
 */
@Slf4j
@Component
@Profile("reset-password")
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ResetPasswordRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext context;
    private final PasswordResetService passwordResetService;
    private final ResetPasswordProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.error("INIT - ResetPasswordRunner -> run() - define RESET_PASSWORD_EMAIL y RESET_PASSWORD");
            System.exit(SpringApplication.exit(context, () -> 1));
            return;
        }

        boolean reset = passwordResetService.reset(properties.email(), properties.password());
        System.exit(SpringApplication.exit(context, () -> reset ? 0 : 1));
    }
}
