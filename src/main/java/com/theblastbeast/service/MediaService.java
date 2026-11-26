package com.theblastbeast.service;

import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.MediaKind;
import com.theblastbeast.domain.vo.MediaAssetKind;
import com.theblastbeast.domain.vo.MediaSummary;
import com.theblastbeast.domain.vo.MediaVariant;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.properties.StorageProperties;
import com.theblastbeast.repository.MediaAssetRepository;
import com.theblastbeast.storage.StorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy/MM");
    private static final boolean WEBP_SUPPORTED =
            ImageIO.getImageWritersByMIMEType("image/webp").hasNext();

    private final MediaAssetRepository mediaAssetRepository;
    private final StorageService storageService;
    private final StorageProperties storageProperties;

    /**
     * Guarda una imagen y sus variantes. El resultado se asocia siempre a un
     * artículo desde {@link AdminArticleService}; no existe una biblioteca
     * compartida de portadas.
     */
    @Transactional
    public MediaAsset upload(MultipartFile file, String altText, String credit) {
        log.debug("INIT - MediaService -> upload() - file={}", file.getOriginalFilename());
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El archivo está vacío");
        }
        if (file.getSize() > storageProperties.maxUploadBytes()) {
            throw new BusinessException(ErrorCode.MEDIA_TOO_LARGE);
        }
        String mimeType = detectMimeType(file);
        byte[] bytes = readBytes(file);
        BufferedImage image = readImage(bytes);

        String folder = LocalDate.now(ZoneOffset.UTC).format(FOLDER);
        String baseName = UUID.randomUUID().toString();
        String originalKey = folder + "/" + baseName + "." + extensionFor(mimeType);

        MediaAsset original = new MediaAsset();
        original.setKind(MediaKind.ORIGINAL);
        original.setStorageKey(originalKey);
        original.setMimeType(mimeType);
        original.setWidth(image.getWidth());
        original.setHeight(image.getHeight());
        original.setBytes(bytes.length);
        original.setAltText(normalize(altText, 300));
        original.setCredit(normalize(credit, 200));
        storageService.store(originalKey, bytes);
        mediaAssetRepository.save(original);

        saveVariant(original, image, MediaKind.THUMB, storageProperties.thumbWidth(), folder, baseName);
        saveVariant(original, image, MediaKind.HERO, storageProperties.heroWidth(), folder, baseName);

        log.info("END - MediaService -> upload() - id={}, key={}", original.getId(), originalKey);
        return original;
    }

    @Transactional
    public MediaAsset updateMetadata(MediaAsset asset, String altText, String credit) {
        if (asset == null) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }
        asset.setAltText(normalize(altText, 300));
        asset.setCredit(normalize(credit, 200));
        return asset;
    }

    /**
     * Elimina una portada y sus variantes. La comprobación de que ya no está
     * asociada a ningún artículo corresponde al servicio de artículos.
     */
    @Transactional
    public void deleteAsset(MediaAsset asset) {
        if (asset == null) {
            return;
        }
        List<MediaAsset> variants = mediaAssetRepository.findByParentIdOrderByKindAsc(asset.getId());
        variants.forEach(variant -> storageService.delete(variant.getStorageKey()));
        mediaAssetRepository.deleteAll(variants);
        storageService.delete(asset.getStorageKey());
        mediaAssetRepository.delete(asset);
        log.info("END - MediaService -> deleteAsset() - id={}", asset.getId());
    }

    public MediaSummary toSummary(MediaAsset asset) {
        if (asset == null) {
            return null;
        }
        return toSummary(asset, mediaAssetRepository.findByParentIdOrderByKindAsc(asset.getId()));
    }

    /**
     * Construye los resúmenes de portada de una lista de artículos cargando todas
     * las variantes en una sola consulta (evita el N+1 en los listados públicos).
     */
    public Map<Long, MediaSummary> toSummaryMap(Collection<MediaAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = assets.stream().map(MediaAsset::getId).toList();
        Map<Long, List<MediaAsset>> variantsByParent = mediaAssetRepository
                .findByParentIdInOrderByKindAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(variant -> variant.getParent().getId()));

        Map<Long, MediaSummary> summaries = new HashMap<>();
        for (MediaAsset asset : assets) {
            summaries.put(
                    asset.getId(),
                    toSummary(asset, variantsByParent.getOrDefault(asset.getId(), List.of())));
        }
        return summaries;
    }

    private MediaSummary toSummary(MediaAsset asset, List<MediaAsset> variants) {
        MediaSummary summary = new MediaSummary();
        summary.setId(asset.getId());
        summary.setKind(MediaAssetKind.ORIGINAL);
        summary.setUrl(url(asset.getStorageKey()));
        summary.setMimeType(asset.getMimeType());
        summary.setWidth(asset.getWidth());
        summary.setHeight(asset.getHeight());
        summary.setAltText(asset.getAltText());
        summary.setCredit(asset.getCredit());
        summary.setVariants(variants.stream().map(this::toVariant).toList());
        return summary;
    }

    private MediaVariant toVariant(MediaAsset asset) {
        MediaVariant variant = new MediaVariant();
        variant.setKind(MediaAssetKind.fromValue(asset.getKind().name().toLowerCase(Locale.ROOT)));
        variant.setUrl(url(asset.getStorageKey()));
        variant.setMimeType(asset.getMimeType());
        variant.setWidth(asset.getWidth());
        variant.setHeight(asset.getHeight());
        variant.setBytes(asset.getBytes());
        return variant;
    }

    private void saveVariant(MediaAsset parent, BufferedImage image, MediaKind kind, int targetWidth,
            String folder, String baseName) {
        int width = Math.min(targetWidth, image.getWidth());
        String format = WEBP_SUPPORTED ? "webp" : "png";
        String mimeType = WEBP_SUPPORTED ? "image/webp" : "image/png";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            Thumbnails.of(image)
                    .width(width)
                    .outputFormat(format)
                    .toOutputStream(output);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.MEDIA_PROCESSING_ERROR,
                    "No se pudo generar la variante " + kind.name().toLowerCase(Locale.ROOT));
        }

        byte[] bytes = output.toByteArray();
        int height = (int) Math.round(image.getHeight() * (width / (double) image.getWidth()));
        String key = folder + "/" + baseName + "-" + kind.name().toLowerCase(Locale.ROOT)
                + "." + (WEBP_SUPPORTED ? "webp" : "png");

        MediaAsset variant = new MediaAsset();
        variant.setParent(parent);
        variant.setKind(kind);
        variant.setStorageKey(key);
        variant.setMimeType(mimeType);
        variant.setWidth(width);
        variant.setHeight(height);
        variant.setBytes(bytes.length);
        storageService.store(key, bytes);
        mediaAssetRepository.save(variant);
    }

    private String detectMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/png", "image/webp", "image/gif" -> contentType.toLowerCase(Locale.ROOT);
            default -> throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        };
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.MEDIA_PROCESSING_ERROR);
        }
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (image == null) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
            return image;
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.MEDIA_PROCESSING_ERROR);
        }
    }

    private String extensionFor(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }

    private String normalize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El texto no puede superar " + maxLength + " caracteres");
        }
        return trimmed;
    }

    private String url(String storageKey) {
        String base = storageProperties.publicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + storageKey;
    }
}
