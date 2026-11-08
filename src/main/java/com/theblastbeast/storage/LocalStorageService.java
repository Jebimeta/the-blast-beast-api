package com.theblastbeast.storage;

import com.theblastbeast.properties.StorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;
    private Path root;

    @PostConstruct
    void initialize() {
        root = Paths.get(properties.localRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("INIT - LocalStorageService -> initialize() - root={}", root);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo crear el directorio de medios", exception);
        }
    }

    @Override
    public void store(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo guardar el archivo " + key, exception);
        }
    }

    @Override
    public Resource load(String key) {
        Path target = resolve(key);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Archivo inexistente: " + key);
        }
        return new FileSystemResource(target);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo borrar el archivo " + key, exception);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Clave de almacenamiento no válida: " + key);
        }
        return target;
    }
}
