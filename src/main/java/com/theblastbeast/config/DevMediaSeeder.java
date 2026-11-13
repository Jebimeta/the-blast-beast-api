package com.theblastbeast.config;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.MediaKind;
import com.theblastbeast.properties.StorageProperties;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.MediaAssetRepository;
import com.theblastbeast.storage.StorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rellena con portadas de ejemplo las noticias del seed de desarrollo.
 *
 * <p>Las imágenes viven en {@code db/dev/media} y solo se copian al
 * almacenamiento cuando el artículo no tiene portada o conserva la portada de
 * prueba anterior. Así se puede ver el portal con imágenes sin depender de
 * servicios externos y sin pisar una portada subida desde la redacción.
 */
@Slf4j
@Component
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class DevMediaSeeder implements ApplicationRunner {

    private static final String PLACEHOLDER_CREDIT = "The Blast Beast";
    private static final String FOLDER = "dev/covers";

    private static final List<Cover> COVERS = List.of(
            new Cover(
                    "metallica-anuncia-gira-europea-2027",
                    "metallica.jpg",
                    "Metallica en directo en el Download Festival 2023",
                    "Doomed Shadow / Wikimedia Commons (CC BY 4.0)"),
            new Cover(
                    "judas-priest-entra-al-estudio",
                    "judas-priest.jpg",
                    "Judas Priest en directo en 2008",
                    "Coelho Voador / Wikimedia Commons (CC BY 2.0)"),
            new Cover(
                    "resurrection-fest-2027-cartel",
                    "resurrection-fest.jpg",
                    "Down en el Resurrection Fest 2014",
                    "Bene Riobó / Wikimedia Commons (CC BY-SA 4.0)"),
            new Cover(
                    "opeth-entrevista-mikael-aekerfeldt",
                    "opeth.jpg",
                    "Opeth en directo en el Hammersmith Odeon",
                    "kitmasterbloke / Wikimedia Commons (CC BY 2.0)"),
            new Cover(
                    "gojira-estrena-the-silent-tide",
                    "gojira.jpg",
                    "Gojira en directo en Toronto en 2016",
                    "Chris Murphy / Wikimedia Commons (CC BY-SA 2.0)"),
            new Cover(
                    "sabaton-tres-fechas-espana",
                    "sabaton.jpg",
                    "Sabaton en el festival Rockavaria",
                    "Klingsi92 / Wikimedia Commons (CC BY-SA 4.0)"),
            new Cover(
                    "iron-maiden-prepara-documental",
                    "iron-maiden.jpg",
                    "Iron Maiden en directo en Madrid en 2016",
                    "dr_zoidberg / Wikimedia Commons (CC BY-SA 2.0)"),
            new Cover(
                    "nightwish-anuncia-paron-indefinido",
                    "nightwish.jpg",
                    "Nightwish en directo en el Hartwall Arena",
                    "Rodrigo Ferrari / Wikimedia Commons (CC BY 2.0)"));

    private final ArticleRepository articleRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        int seeded = 0;
        for (Cover cover : COVERS) {
            if (seed(cover)) {
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("INIT - DevMediaSeeder -> run() - portadas de ejemplo={}", seeded);
        }
    }

    private boolean seed(Cover cover) {
        ClassPathResource resource =
                new ClassPathResource("db/dev/media/" + cover.file());
        if (!resource.exists()) {
            log.warn("DevMediaSeeder: no existe el recurso {}", resource.getPath());
            return false;
        }

        byte[] bytes;
        BufferedImage image;
        try (InputStream input = resource.getInputStream()) {
            bytes = input.readAllBytes();
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "No se pudo leer la portada de ejemplo " + cover.file(), exception);
        }
        if (image == null) {
            log.warn("DevMediaSeeder: imagen no válida {}", cover.file());
            return false;
        }

        Boolean seeded = transactionTemplate.execute(status -> seedInTransaction(cover, bytes, image));
        return Boolean.TRUE.equals(seeded);
    }

    private boolean seedInTransaction(Cover cover, byte[] bytes, BufferedImage image) {
        Article article = articleRepository.findBySlug(cover.slug()).orElse(null);
        if (article == null) {
            return false;
        }
        MediaAsset current = article.getCoverMedia();
        if (current != null && !PLACEHOLDER_CREDIT.equals(current.getCredit())) {
            return false;
        }

        String baseName = UUID.randomUUID().toString();
        MediaAsset original = new MediaAsset();
        original.setKind(MediaKind.ORIGINAL);
        original.setStorageKey(FOLDER + "/" + baseName + ".jpg");
        original.setMimeType("image/jpeg");
        original.setWidth(image.getWidth());
        original.setHeight(image.getHeight());
        original.setBytes(bytes.length);
        original.setAltText(cover.altText());
        original.setCredit(cover.credit());
        storageService.store(original.getStorageKey(), bytes);
        mediaAssetRepository.save(original);

        saveVariant(original, image, MediaKind.THUMB, storageProperties.thumbWidth(), baseName);
        saveVariant(original, image, MediaKind.HERO, storageProperties.heroWidth(), baseName);

        article.setCoverMedia(original);
        articleRepository.save(article);
        return true;
    }

    private void saveVariant(
            MediaAsset parent, BufferedImage image, MediaKind kind, int targetWidth, String baseName) {
        int width = Math.min(targetWidth, image.getWidth());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            Thumbnails.of(image).width(width).outputFormat("jpg").toOutputStream(output);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "No se pudo generar la variante " + kind.name().toLowerCase(Locale.ROOT),
                    exception);
        }

        byte[] bytes = output.toByteArray();
        int height = (int) Math.round(image.getHeight() * (width / (double) image.getWidth()));
        MediaAsset variant = new MediaAsset();
        variant.setParent(parent);
        variant.setKind(kind);
        variant.setStorageKey(
                FOLDER + "/" + baseName + "-" + kind.name().toLowerCase(Locale.ROOT) + ".jpg");
        variant.setMimeType("image/jpeg");
        variant.setWidth(width);
        variant.setHeight(height);
        variant.setBytes(bytes.length);
        storageService.store(variant.getStorageKey(), bytes);
        mediaAssetRepository.save(variant);
    }

    private record Cover(String slug, String file, String altText, String credit) {}
}
