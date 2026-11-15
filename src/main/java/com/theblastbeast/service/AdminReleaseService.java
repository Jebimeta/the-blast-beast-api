package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Release;
import com.theblastbeast.domain.entity.ReleaseKind;
import com.theblastbeast.domain.vo.ReleaseAdminResponse;
import com.theblastbeast.domain.vo.ReleaseWriteRequest;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.ReleaseMapper;
import com.theblastbeast.repository.BandRepository;
import com.theblastbeast.repository.ReleaseRepository;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReleaseService {

    private final ReleaseRepository releaseRepository;
    private final BandRepository bandRepository;
    private final ReleaseMapper mapper;
    private final SlugService slugService;

    @Transactional(readOnly = true)
    public List<ReleaseAdminResponse> list(String q, String month) {
        String query = q == null ? "" : q.trim();
        YearMonth filter = parseOptionalMonth(month);
        return releaseRepository.searchAdmin(query).stream()
                .filter(release -> filter == null
                        || YearMonth.from(release.getReleaseDate()).equals(filter))
                .map(mapper::toAdminResponse)
                .toList();
    }

    @Transactional
    public ReleaseAdminResponse create(ReleaseWriteRequest request) {
        Release release = new Release();
        release.setSlug(resolveSlug(request, null));
        apply(release, request);
        releaseRepository.saveAndFlush(release);
        log.info("END - AdminReleaseService -> create() - id={}, slug={}",
                release.getId(), release.getSlug());
        return mapper.toAdminResponse(release);
    }

    @Transactional
    public ReleaseAdminResponse update(Long id, ReleaseWriteRequest request) {
        Release release = find(id);
        apply(release, request);
        releaseRepository.saveAndFlush(release);
        log.info("END - AdminReleaseService -> update() - id={}", id);
        return mapper.toAdminResponse(release);
    }

    @Transactional
    public void delete(Long id) {
        Release release = find(id);
        releaseRepository.delete(release);
        log.info("END - AdminReleaseService -> delete() - id={}", id);
    }

    private void apply(Release release, ReleaseWriteRequest request) {
        release.setTitle(request.getTitle().trim());
        release.setArtistName(request.getArtistName().trim());
        release.setBand(findBand(request.getBandId()));
        release.setKind(ReleaseKind.valueOf(request.getType().name()));
        release.setReleaseDate(request.getReleaseDate());
        String genre = trimToNull(request.getGenre());
        release.setGenre(genre);
        release.setGenreSlug(genre == null ? null : slugService.slugify(genre));
        release.setLabel(trimToNull(request.getLabel()));
        release.setListenUrl(listenUrl(request.getListenUrl()));
        release.setNotes(trimToNull(request.getNotes()));
        release.setFeatured(Boolean.TRUE.equals(request.getFeatured()));
        release.setHidden(Boolean.TRUE.equals(request.getHidden()));
    }

    private Band findBand(Long bandId) {
        if (bandId == null) {
            return null;
        }
        return bandRepository.findById(bandId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_BAND_NOT_FOUND));
    }

    private Release find(Long id) {
        return releaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RELEASE_NOT_FOUND));
    }

    private String resolveSlug(ReleaseWriteRequest request, Long currentId) {
        String base = slugService.slugify(request.getArtistName() + " " + request.getTitle()
                + " " + request.getReleaseDate().getYear());
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se pudo generar un slug a partir del artista y el título");
        }
        return slugService.unique(base, slug -> releaseRepository.existsBySlugAndIdNot(slug,
                currentId == null ? -1L : currentId));
    }

    private YearMonth parseOptionalMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return null;
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El mes debe tener el formato YYYY-MM");
        }
    }

    private String listenUrl(String value) {
        String url = trimToNull(value);
        if (url != null && !url.matches("(?i)^https?://\\S+$")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El enlace para escuchar debe empezar por http:// o https://");
        }
        return url;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
