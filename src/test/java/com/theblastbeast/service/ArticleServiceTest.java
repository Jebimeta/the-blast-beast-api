package com.theblastbeast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.theblastbeast.domain.entity.Article;
import com.theblastbeast.domain.entity.ArticleStatus;
import com.theblastbeast.domain.entity.Category;
import com.theblastbeast.domain.entity.User;
import com.theblastbeast.domain.vo.ArticleDetail;
import com.theblastbeast.domain.vo.ArticlePage;
import com.theblastbeast.domain.vo.ArticleSort;
import com.theblastbeast.domain.vo.ArticleSummary;
import com.theblastbeast.exception.BusinessException;
import com.theblastbeast.exception.ErrorCode;
import com.theblastbeast.mapper.ArticleMapper;
import com.theblastbeast.repository.ArticleFilter;
import com.theblastbeast.repository.ArticleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private ArticleService articleService;

    private Article article;

    @BeforeEach
    void setUp() {
        User author = new User();
        author.setDisplayName("Redacción Blast Beast");

        Category category = new Category();
        category.setSlug("conciertos");
        category.setName("Conciertos");

        article = new Article();
        article.setId(1L);
        article.setSlug("metallica-anuncia-gira");
        article.setTitle("Metallica anuncia gira");
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setAuthor(author);
        article.setCategory(category);
    }

    @Test
    @DisplayName("list normaliza página y tamaño antes de consultar")
    void listNormalizaPaginacion() {
        Page<Article> page = new PageImpl<>(List.of(article), PageRequest.of(0, 50), 1);
        when(articleRepository.search(any(ArticleFilter.class), anyInt(), anyInt()))
                .thenReturn(page);
        when(articleMapper.toSummaries(any())).thenReturn(List.of(new ArticleSummary()));

        ArticlePage result = articleService.list(
                new ArticleFilter(null, null, null, null, ArticleSort.LATEST), -3, 500);

        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(articleRepository).search(any(ArticleFilter.class), pageCaptor.capture(),
                sizeCaptor.capture());

        assertThat(pageCaptor.getValue()).isZero();
        assertThat(sizeCaptor.getValue()).isEqualTo(50);
        assertThat(result.getPage()).isZero();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("list aplica valores por defecto cuando no llegan parámetros")
    void listAplicaValoresPorDefecto() {
        when(articleRepository.search(any(ArticleFilter.class), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of()));
        when(articleMapper.toSummaries(any())).thenReturn(List.of());

        articleService.list(new ArticleFilter(null, null, null, null, null), null, null);

        verify(articleRepository).search(any(ArticleFilter.class), eq(0), eq(12));
    }

    @Test
    @DisplayName("featured pide los cinco artículos publicados más recientes")
    void featuredPideCincoArticulos() {
        when(articleRepository.findByStatusOrderByPublishedAtDesc(
                eq(ArticleStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(List.of(article));
        when(articleMapper.toSummaries(List.of(article)))
                .thenReturn(List.of(new ArticleSummary()));

        List<ArticleSummary> result = articleService.featured();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findByStatusOrderByPublishedAtDesc(
                eq(ArticleStatus.PUBLISHED), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("detail devuelve el artículo publicado mapeado")
    void detailDevuelveArticuloPublicado() {
        ArticleDetail detail = new ArticleDetail();
        when(articleRepository.findBySlug("metallica-anuncia-gira"))
                .thenReturn(Optional.of(article));
        when(articleMapper.toDetail(article)).thenReturn(detail);

        ArticleDetail result = articleService.detail("metallica-anuncia-gira");

        assertThat(result).isSameAs(detail);
    }

    @Test
    @DisplayName("detail lanza 404 cuando el artículo no existe")
    void detailLanza404SiNoExiste() {
        when(articleRepository.findBySlug("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.detail("no-existe"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ARTICLE_NOT_FOUND));
    }

    @Test
    @DisplayName("detail lanza 410 cuando el artículo está archivado")
    void detailLanza410SiEstaArchivado() {
        article.setStatus(ArticleStatus.ARCHIVED);
        when(articleRepository.findBySlug("metallica-anuncia-gira"))
                .thenReturn(Optional.of(article));

        assertThatThrownBy(() -> articleService.detail("metallica-anuncia-gira"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ARTICLE_NOT_PUBLISHED));

        verify(articleMapper, never()).toDetail(any());
    }

    @Test
    @DisplayName("detail lanza 404 cuando el artículo es un borrador")
    void detailLanza404SiEsBorrador() {
        article.setStatus(ArticleStatus.DRAFT);
        when(articleRepository.findBySlug("metallica-anuncia-gira"))
                .thenReturn(Optional.of(article));

        assertThatThrownBy(() -> articleService.detail("metallica-anuncia-gira"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ARTICLE_NOT_FOUND));
    }

    @Test
    @DisplayName("search exige al menos dos caracteres")
    void searchExigeDosCaracteres() {
        assertThatThrownBy(() -> articleService.search(" a ", 0, 10))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(articleRepository, never()).search(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("search delega en el listado ordenado por relevancia")
    void searchDelegaEnListadoPorRelevancia() {
        when(articleRepository.search(any(ArticleFilter.class), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(article)));
        when(articleMapper.toSummaries(any())).thenReturn(List.of(new ArticleSummary()));

        articleService.search("gira", 0, 10);

        ArgumentCaptor<ArticleFilter> filterCaptor = ArgumentCaptor.forClass(ArticleFilter.class);
        verify(articleRepository).search(filterCaptor.capture(), eq(0), eq(10));
        assertThat(filterCaptor.getValue().q()).isEqualTo("gira");
        assertThat(filterCaptor.getValue().sort()).isEqualTo(ArticleSort.RELEVANCE);
    }
}
