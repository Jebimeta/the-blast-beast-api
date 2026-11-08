package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.vo.TagResponse;
import com.theblastbeast.domain.vo.TagWriteRequest;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.AdminTaxonomyMapper;
import com.theblastbeast.repository.TagRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTagService {

    private final TagRepository tagRepository;
    private final AdminTaxonomyMapper mapper;
    private final SlugService slugService;

    @Transactional(readOnly = true)
    public List<TagResponse> list(String q) {
        String query = q == null ? "" : q.trim();
        return tagRepository.search(query).stream()
                .map(mapper::toTagResponse)
                .toList();
    }

    @Transactional
    public TagResponse create(TagWriteRequest request) {
        Tag tag = new Tag();
        tag.setSlug(resolveSlug(request.getSlug(), request.getName(), null));
        tag.setName(request.getName().trim());
        tagRepository.save(tag);
        log.info("END - AdminTagService -> create() - id={}, slug={}", tag.getId(), tag.getSlug());
        return mapper.toTagResponse(tag);
    }

    @Transactional
    public TagResponse update(Long id, TagWriteRequest request) {
        Tag tag = find(id);
        if (StringUtils.hasText(request.getSlug())) {
            tag.setSlug(resolveSlug(request.getSlug(), request.getName(), id));
        }
        tag.setName(request.getName().trim());
        tagRepository.save(tag);
        log.info("END - AdminTagService -> update() - id={}", id);
        return mapper.toTagResponse(tag);
    }

    @Transactional
    public void delete(Long id) {
        Tag tag = find(id);
        tagRepository.delete(tag);
        log.info("END - AdminTagService -> delete() - id={}", id);
    }

    private String resolveSlug(String requested, String name, Long currentId) {
        String base = slugService.slugify(StringUtils.hasText(requested) ? requested : name);
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se pudo generar un slug a partir del nombre");
        }
        return slugService.unique(base, slug -> tagRepository.existsBySlugAndIdNot(slug,
                currentId == null ? -1L : currentId));
    }

    private Tag find(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TAG_NOT_FOUND));
    }
}
