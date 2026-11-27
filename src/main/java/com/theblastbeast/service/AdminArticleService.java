package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleRevision;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.AdminArticleDetail;
import com.theblastbeast.domain.vo.AdminArticlePage;
import com.theblastbeast.domain.vo.AdminArticleStatus;
import com.theblastbeast.domain.vo.AdminArticleSummary;
import com.theblastbeast.domain.vo.ArticleRevisionResponse;
import com.theblastbeast.domain.vo.ArticleWriteRequest;
import com.theblastbeast.domain.vo.MarkdownPreviewResponse;
import com.theblastbeast.domain.vo.MediaUpdateRequest;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.AdminArticleMapper;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.ArticleRevisionRepository;
import com.theblastbeast.repository.BandRepository;
import com.theblastbeast.repository.CategoryRepository;
import com.theblastbeast.repository.TagRepository;
import com.theblastbeast.repository.UserRepository;
import com.theblastbeast.security.CurrentUserProvider;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminArticleService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_BANDS = 20;
    private static final int MAX_TAGS = 20;
    private static final int MAX_BAND_NAME = 120;
    private static final int MAX_TAG_NAME = 80;

    private final ArticleRepository articleRepository;
    private final ArticleRevisionRepository revisionRepository;
    private final CategoryRepository categoryRepository;
    private final BandRepository bandRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final AdminArticleMapper mapper;
    private final MarkdownService markdownService;
    private final SlugService slugService;
    private final MediaService mediaService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public AdminArticlePage list(Integer page, Integer size, AdminArticleStatus status, String q) {
        log.debug("INIT - AdminArticleService -> list() - status={}, q={}", status, q);
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Specification<Article> specification = buildSpecification(status, q);
        Page<Article> result = articleRepository.findAll(specification,
                PageRequest.of(safePage, safeSize,
                        Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));

        AdminArticlePage response = new AdminArticlePage();
        response.setContent(result.getContent().stream().map(this::toSummary).toList());
        response.setPage(result.getNumber());
        response.setSize(result.getSize());
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setFirst(result.isFirst());
        response.setLast(result.isLast());
        log.debug("END - AdminArticleService -> list() - {} artículos", result.getNumberOfElements());
        return response;
    }

    @Transactional(readOnly = true)
    public AdminArticleDetail get(Long id) {
        return toDetail(find(id));
    }

    @Transactional
    public AdminArticleDetail create(ArticleWriteRequest request) {
        log.debug("INIT - AdminArticleService -> create() - title={}", request.getTitle());
        User author = userRepository.getReferenceById(currentUserProvider.id());

        Article article = new Article();
        applyContent(article, request, null);
        article.setSlug(resolveSlug(request.getSlug(), request.getTitle(), null));
        article.setAuthor(author);
        article.setStatus(ArticleStatus.DRAFT);
        articleRepository.save(article);
        saveRevision(article, author);

        AdminArticleDetail response = toDetail(article);
        log.info("END - AdminArticleService -> create() - id={}, slug={}", article.getId(), article.getSlug());
        return response;
    }

    @Transactional
    public AdminArticleDetail update(Long id, ArticleWriteRequest request) {
        log.debug("INIT - AdminArticleService -> update() - id={}", id);
        Article article = find(id);
        User editor = userRepository.getReferenceById(currentUserProvider.id());

        saveRevision(article, editor);
        applyContent(article, request, id);
        if (StringUtils.hasText(request.getSlug())) {
            article.setSlug(resolveSlug(request.getSlug(), request.getTitle(), id));
        }
        articleRepository.save(article);

        AdminArticleDetail response = toDetail(article);
        log.info("END - AdminArticleService -> update() - id={}", id);
        return response;
    }

    @Transactional
    public void delete(Long id) {
        Article article = find(id);
        MediaAsset cover = article.getCoverMedia();
        articleRepository.delete(article);
        articleRepository.flush();
        deleteCoverIfUnused(cover);
        log.info("END - AdminArticleService -> delete() - id={}", id);
    }

    @Transactional
    public AdminArticleDetail uploadCover(Long id, MultipartFile file, String altText, String credit) {
        log.debug("INIT - AdminArticleService -> uploadCover() - id={}", id);
        Article article = find(id);
        MediaAsset previous = article.getCoverMedia();
        MediaAsset cover = mediaService.upload(file, coverAlt(article, altText), credit);
        article.setCoverMedia(cover);
        articleRepository.saveAndFlush(article);
        deleteCoverIfUnused(previous);

        AdminArticleDetail response = toDetail(article);
        log.info("END - AdminArticleService -> uploadCover() - id={}, cover={}", id, cover.getId());
        return response;
    }

    @Transactional
    public AdminArticleDetail updateCover(Long id, MediaUpdateRequest request) {
        Article article = find(id);
        MediaAsset cover = article.getCoverMedia();
        if (cover == null) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }
        mediaService.updateMetadata(cover, coverAlt(article, request.getAltText()), request.getCredit());
        return toDetail(article);
    }

    @Transactional
    public AdminArticleDetail removeCover(Long id) {
        Article article = find(id);
        MediaAsset cover = article.getCoverMedia();
        if (cover != null) {
            article.setCoverMedia(null);
            articleRepository.saveAndFlush(article);
            deleteCoverIfUnused(cover);
            log.info("END - AdminArticleService -> removeCover() - id={}", id);
        }
        return toDetail(article);
    }

    @Transactional
    public AdminArticleDetail publish(Long id) {
        Article article = find(id);
        ensureReady(article);
        article.setStatus(ArticleStatus.PUBLISHED);
        if (article.getPublishedAt() == null) {
            article.setPublishedAt(OffsetDateTime.now());
        }
        articleRepository.save(article);
        log.info("END - AdminArticleService -> publish() - id={}", id);
        return toDetail(article);
    }

    @Transactional
    public AdminArticleDetail unpublish(Long id) {
        Article article = find(id);
        article.setStatus(ArticleStatus.DRAFT);
        articleRepository.save(article);
        log.info("END - AdminArticleService -> unpublish() - id={}", id);
        return toDetail(article);
    }

    @Transactional
    public AdminArticleDetail archive(Long id) {
        Article article = find(id);
        article.setStatus(ArticleStatus.ARCHIVED);
        articleRepository.save(article);
        log.info("END - AdminArticleService -> archive() - id={}", id);
        return toDetail(article);
    }

    @Transactional(readOnly = true)
    public List<ArticleRevisionResponse> revisions(Long id) {
        find(id);
        return revisionRepository.findByArticleIdOrderByCreatedAtDesc(id).stream()
                .map(mapper::toRevision)
                .toList();
    }

    @Transactional
    public AdminArticleDetail restore(Long id, Long revisionId) {
        Article article = find(id);
        ArticleRevision revision = revisionRepository.findByIdAndArticleId(revisionId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_REVISION_NOT_FOUND));
        User editor = userRepository.getReferenceById(currentUserProvider.id());

        saveRevision(article, editor);
        article.setTitle(revision.getTitle());
        article.setBodyMd(revision.getBodyMd());
        article.setBodyHtml(markdownService.render(revision.getBodyMd()));
        article.setReadingMinutes(markdownService.readingMinutes(revision.getBodyMd()));
        articleRepository.save(article);

        log.info("END - AdminArticleService -> restore() - id={}, revision={}", id, revisionId);
        return toDetail(article);
    }

    public MarkdownPreviewResponse preview(String bodyMd) {
        MarkdownPreviewResponse response = new MarkdownPreviewResponse();
        response.setBodyHtml(markdownService.render(bodyMd));
        return response;
    }

    private void applyContent(Article article, ArticleWriteRequest request, Long currentId) {
        article.setTitle(request.getTitle().trim());
        article.setExcerpt(request.getExcerpt().trim());
        article.setBodyMd(request.getBodyMd());
        article.setBodyHtml(markdownService.render(request.getBodyMd()));
        article.setReadingMinutes(markdownService.readingMinutes(request.getBodyMd()));
        article.setCategory(resolveCategory(request.getCategoryId()));
        article.setBands(resolveBands(request.getBandNames()));
        article.setTags(resolveTags(request.getTagNames()));
        article.setSeoTitle(trimToNull(request.getSeoTitle()));
        article.setSeoDescription(trimToNull(request.getSeoDescription()));
        article.setCanonicalUrl(trimToNull(request.getCanonicalUrl()));
    }

    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private Set<Band> resolveBands(List<String> names) {
        Set<Band> bands = new LinkedHashSet<>();
        for (String name : normalizeNames(names, MAX_BANDS, MAX_BAND_NAME, "banda")) {
            String slug = slugService.slugify(name);
            if (!StringUtils.hasText(slug)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "No se pudo generar un slug a partir del nombre de la banda");
            }
            Band band = bandRepository.findBySlug(slug)
                    .or(() -> bandRepository.findFirstByNameIgnoreCase(name))
                    .orElseGet(() -> createBand(name, slug));
            bands.add(band);
        }
        return bands;
    }

    private Set<Tag> resolveTags(List<String> names) {
        Set<Tag> tags = new LinkedHashSet<>();
        for (String name : normalizeNames(names, MAX_TAGS, MAX_TAG_NAME, "etiqueta")) {
            String slug = slugService.slugify(name);
            if (!StringUtils.hasText(slug)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "No se pudo generar un slug a partir del nombre de la etiqueta");
            }
            Tag tag = tagRepository.findBySlug(slug)
                    .or(() -> tagRepository.findFirstByNameIgnoreCase(name))
                    .orElseGet(() -> createTag(name, slug));
            tags.add(tag);
        }
        return tags;
    }

    private Band createBand(String name, String slug) {
        Band band = new Band();
        band.setName(name);
        band.setSlug(slugService.unique(slug, MAX_BAND_NAME, bandRepository::existsBySlug));
        bandRepository.save(band);
        log.info("Banda creada desde el editor - id={}, slug={}", band.getId(), band.getSlug());
        return band;
    }

    private Tag createTag(String name, String slug) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(slugService.unique(slug, MAX_TAG_NAME, tagRepository::existsBySlug));
        tagRepository.save(tag);
        log.info("Etiqueta creada desde el editor - id={}, slug={}", tag.getId(), tag.getSlug());
        return tag;
    }

    private List<String> normalizeNames(List<String> names, int maxItems, int maxLength, String label) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : names) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String name = raw.trim();
            if (name.length() > maxLength) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "El nombre de la " + label + " no puede superar " + maxLength + " caracteres");
            }
            unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        if (unique.size() > maxItems) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se pueden añadir más de " + maxItems + " " + label + "s");
        }
        return List.copyOf(unique.values());
    }

    private String coverAlt(Article article, String altText) {
        return StringUtils.hasText(altText) ? altText.trim() : article.getTitle();
    }

    private void deleteCoverIfUnused(MediaAsset cover) {
        if (cover == null) {
            return;
        }
        if (!articleRepository.existsByCoverMediaId(cover.getId())) {
            mediaService.deleteAsset(cover);
        }
    }

    private String resolveSlug(String requested, String title, Long currentId) {
        String base = slugService.slugify(StringUtils.hasText(requested) ? requested : title);
        if (!StringUtils.hasText(base)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "No se pudo generar un slug a partir del título");
        }
        return slugService.unique(base, slug -> articleRepository.findBySlug(slug)
                .filter(existing -> !existing.getId().equals(currentId))
                .isPresent());
    }

    private void ensureReady(Article article) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(article.getTitle())) {
            missing.add("título");
        }
        if (!StringUtils.hasText(article.getExcerpt())) {
            missing.add("entradilla");
        }
        if (!StringUtils.hasText(article.getBodyMd())) {
            missing.add("cuerpo");
        }
        if (article.getCategory() == null) {
            missing.add("categoría");
        }
        if (article.getCoverMedia() == null) {
            missing.add("portada");
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_READY,
                    "Faltan campos obligatorios: " + String.join(", ", missing));
        }
    }

    private void saveRevision(Article article, User editor) {
        ArticleRevision revision = new ArticleRevision();
        revision.setArticle(article);
        revision.setEditor(editor);
        revision.setTitle(article.getTitle());
        revision.setBodyMd(article.getBodyMd());
        revisionRepository.save(revision);
    }

    private Article find(Long id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    private AdminArticleSummary toSummary(Article article) {
        AdminArticleSummary summary = mapper.toSummary(article);
        summary.setCover(mediaService.toSummary(article.getCoverMedia()));
        return summary;
    }

    private AdminArticleDetail toDetail(Article article) {
        AdminArticleDetail detail = mapper.toDetail(article);
        detail.setCover(mediaService.toSummary(article.getCoverMedia()));
        return detail;
    }

    private Specification<Article> buildSpecification(AdminArticleStatus status, String q) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"),
                        ArticleStatus.valueOf(status.name())));
            }
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("slug")), like)));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
