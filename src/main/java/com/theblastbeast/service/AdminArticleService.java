package com.theblastbeast.service;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleRevision;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Band;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.MediaAsset;
import com.theblastbeast.domain.entity.MediaKind;
import com.theblastbeast.domain.entity.Tag;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.AdminArticleDetail;
import com.theblastbeast.domain.vo.AdminArticlePage;
import com.theblastbeast.domain.vo.AdminArticleStatus;
import com.theblastbeast.domain.vo.AdminArticleSummary;
import com.theblastbeast.domain.vo.ArticleRevisionResponse;
import com.theblastbeast.domain.vo.ArticleWriteRequest;
import com.theblastbeast.domain.vo.MarkdownPreviewResponse;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.AdminArticleMapper;
import com.theblastbeast.repository.ArticleRepository;
import com.theblastbeast.repository.ArticleRevisionRepository;
import com.theblastbeast.repository.BandRepository;
import com.theblastbeast.repository.CategoryRepository;
import com.theblastbeast.repository.MediaAssetRepository;
import com.theblastbeast.repository.TagRepository;
import com.theblastbeast.repository.UserRepository;
import com.theblastbeast.security.CurrentUserProvider;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminArticleService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final ArticleRepository articleRepository;
    private final ArticleRevisionRepository revisionRepository;
    private final CategoryRepository categoryRepository;
    private final BandRepository bandRepository;
    private final TagRepository tagRepository;
    private final MediaAssetRepository mediaAssetRepository;
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
        articleRepository.delete(article);
        log.info("END - AdminArticleService -> delete() - id={}", id);
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
        article.setBands(resolveBands(request.getBandIds()));
        article.setTags(resolveTags(request.getTagIds()));
        article.setCoverMedia(resolveCover(request.getCoverMediaId()));
        article.setSeoTitle(trimToNull(request.getSeoTitle()));
        article.setSeoDescription(trimToNull(request.getSeoDescription()));
        article.setCanonicalUrl(trimToNull(request.getCanonicalUrl()));
    }

    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private Set<Band> resolveBands(List<Long> bandIds) {
        if (bandIds == null || bandIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(bandIds);
        List<Band> bands = bandRepository.findAllById(uniqueIds);
        if (bands.size() != uniqueIds.size()) {
            throw new BusinessException(ErrorCode.BAND_NOT_FOUND);
        }
        return new LinkedHashSet<>(bands);
    }

    private Set<Tag> resolveTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(tagIds);
        List<Tag> tags = tagRepository.findAllById(uniqueIds);
        if (tags.size() != uniqueIds.size()) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }
        return new LinkedHashSet<>(tags);
    }

    private MediaAsset resolveCover(Long mediaId) {
        if (mediaId == null) {
            return null;
        }
        return mediaAssetRepository.findById(mediaId)
                .filter(asset -> asset.getKind() == MediaKind.ORIGINAL)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_NOT_FOUND));
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
