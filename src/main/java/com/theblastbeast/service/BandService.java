package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.vo.BandDetail;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.BandMapper;
import com.theblastbeast.repository.BandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BandService {

    private final BandRepository bandRepository;
    private final BandMapper bandMapper;
    private final ArticleService articleService;

    @Transactional(readOnly = true)
    public BandDetail detail(String slug) {
        log.debug("INIT - BandService -> detail() - slug={}", slug);
        Band band = bandRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAND_NOT_FOUND));

        BandDetail response = bandMapper.toDetail(band);
        response.setArticles(articleService.byBand(slug));
        log.debug("END - BandService -> detail() - slug={}", slug);
        return response;
    }
}
