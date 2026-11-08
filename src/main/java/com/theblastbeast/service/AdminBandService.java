package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.vo.BandResponse;
import com.theblastbeast.domain.vo.BandWriteRequest;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.AdminTaxonomyMapper;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.BandRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBandService {

    private final BandRepository bandRepository;
    private final ArticleRepository articleRepository;
    private final AdminTaxonomyMapper mapper;
    private final SlugService slugService;

    @Transactional(readOnly = true)
    public List<BandResponse> list(String q) {
        String query = q == null ? "" : q.trim();
        return bandRepository.search(query).stream()
                .map(mapper::toBandResponse)
                .toList();
    }

    @Transactional
    public BandResponse create(BandWriteRequest request) {
        Band band = new Band();
        band.setSlug(resolveSlug(request.getSlug(), request.getName(), null));
        apply(band, request);
        bandRepository.save(band);
        log.info("END - AdminBandService -> create() - id={}, slug={}", band.getId(), band.getSlug());
        return mapper.toBandResponse(band);
    }

    @Transactional
    public BandResponse update(Long id, BandWriteRequest request) {
        Band band = find(id);
        if (StringUtils.hasText(request.getSlug())) {
            band.setSlug(resolveSlug(request.getSlug(), request.getName(), id));
        }
        apply(band, request);
        bandRepository.save(band);
        log.info("END - AdminBandService -> update() - id={}", id);
        return mapper.toBandResponse(band);
    }

    @Transactional
    public void delete(Long id) {
        Band band = find(id);
        if (articleRepository.existsByBandsId(id)) {
            throw new BusinessException(ErrorCode.BAND_IN_USE);
        }
        bandRepository.delete(band);
        log.info("END - AdminBandService -> delete() - id={}", id);
    }

    private void apply(Band band, BandWriteRequest request) {
        band.setName(request.getName().trim());
        band.setCountry(trimToNull(request.getCountry()));
        band.setFormedYear(request.getFormedYear());
        band.setGenre(trimToNull(request.getGenre()));
        band.setWebsite(trimToNull(request.getWebsite()));
        band.setBandcampUrl(trimToNull(request.getBandcampUrl()));
        band.setSpotifyUrl(trimToNull(request.getSpotifyUrl()));
    }

    private String resolveSlug(String requested, String name, Long currentId) {
        String base = slugService.slugify(StringUtils.hasText(requested) ? requested : name);
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se pudo generar un slug a partir del nombre");
        }
        return slugService.unique(base, slug -> bandRepository.existsBySlugAndIdNot(slug,
                currentId == null ? -1L : currentId));
    }

    private Band find(Long id) {
        return bandRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
