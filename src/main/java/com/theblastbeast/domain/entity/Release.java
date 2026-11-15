package com.theblastbeast.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Lanzamiento musical del radar editorial.
 *
 * <p>El artista se guarda desnormalizado para poder anunciar proyectos que aún
 * no tienen ficha de banda. Si existe ficha, {@link #band} enlaza a ella.
 */
@Entity
@Table(name = "releases")
@Getter
@Setter
@NoArgsConstructor
@BatchSize(size = 32)
public class Release {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "artist_name", nullable = false, length = 160)
    private String artistName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "band_id")
    private Band band;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ReleaseKind kind;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Column(length = 80)
    private String genre;

    @Column(name = "genre_slug", length = 80)
    private String genreSlug;

    @Column(length = 120)
    private String label;

    @Column(name = "listen_url", length = 600)
    private String listenUrl;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false)
    private boolean hidden;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
